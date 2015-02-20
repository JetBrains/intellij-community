package org.jetbrains.io.fastCgi;

import com.intellij.util.Consumer;
import gnu.trove.TIntObjectHashMap;
import gnu.trove.TIntObjectProcedure;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.CharsetUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.io.Decoder;

import static org.jetbrains.io.fastCgi.FastCgiService.LOG;

public class FastCgiDecoder extends Decoder implements Decoder.FullMessageConsumer<Void> {
  private enum State {
    HEADER, CONTENT
  }

  private State state = State.HEADER;

  private enum ProtocolStatus {
    REQUEST_COMPLETE, CANT_MPX_CONN, OVERLOADED, UNKNOWN_ROLE
  }

  public static final class RecordType {
    public static final int END_REQUEST = 3;
    public static final int STDOUT = 6;
    public static final int STDERR = 7;
  }

  private int type;
  private int id;
  private int contentLength;
  private int paddingLength;

  private final TIntObjectHashMap<ByteBuf> dataBuffers = new TIntObjectHashMap<ByteBuf>();

  private final Consumer<String> errorOutputConsumer;
  private final FastCgiService responseHandler;

  public FastCgiDecoder(@NotNull Consumer<String> errorOutputConsumer, @NotNull FastCgiService responseHandler) {
    this.errorOutputConsumer = errorOutputConsumer;
    this.responseHandler = responseHandler;
  }

  @Override
  protected void messageReceived(@NotNull ChannelHandlerContext context, @NotNull ByteBuf input) throws Exception {
    while (true) {
      switch (state) {
        case HEADER: {
          if (paddingLength > 0) {
            if (input.readableBytes() > paddingLength) {
              input.skipBytes(paddingLength);
              paddingLength = 0;
            }
            else {
              paddingLength -= input.readableBytes();
              input.skipBytes(input.readableBytes());
              return;
            }
          }

          ByteBuf buffer = getBufferIfSufficient(input, FastCgiConstants.HEADER_LENGTH, context);
          if (buffer == null) {
            return;
          }

          decodeHeader(buffer);
          state = State.CONTENT;
        }

        case CONTENT: {
          if (contentLength > 0) {
            readContent(input, context, contentLength, this);
          }
          state = State.HEADER;
        }
      }
    }
  }

  @Override
  public void channelInactive(ChannelHandlerContext context) throws Exception {
    try {
      if (!dataBuffers.isEmpty()) {
        dataBuffers.forEachEntry(new TIntObjectProcedure<ByteBuf>() {
          @Override
          public boolean execute(int a, ByteBuf buffer) {
            try {
              buffer.release();
            }
            catch (Throwable e) {
              LOG.error(e);
            }
            return true;
          }
        });
        dataBuffers.clear();
      }
    }
    finally {
      super.channelInactive(context);
    }
  }

  private void decodeHeader(@NotNull ByteBuf buffer) {
    buffer.skipBytes(1);
    type = buffer.readUnsignedByte();
    id = buffer.readUnsignedShort();
    contentLength = buffer.readUnsignedShort();
    paddingLength = buffer.readUnsignedByte();
    buffer.skipBytes(1);
  }

  @Override
  public Void contentReceived(@NotNull ByteBuf buffer, @NotNull ChannelHandlerContext context, boolean isCumulateBuffer) {
    switch (type) {
      case RecordType.END_REQUEST:
        int appStatus = buffer.readInt();
        int protocolStatus = buffer.readUnsignedByte();
        if (appStatus != 0 || protocolStatus != ProtocolStatus.REQUEST_COMPLETE.ordinal()) {
          LOG.warn("Protocol status " + protocolStatus);
          dataBuffers.remove(id);
          responseHandler.responseReceived(id, null);
        }
        else if (protocolStatus == ProtocolStatus.REQUEST_COMPLETE.ordinal()) {
          responseHandler.responseReceived(id, dataBuffers.remove(id));
        }
        break;

      case RecordType.STDOUT:
        ByteBuf data = dataBuffers.get(id);
        ByteBuf sliced = isCumulateBuffer ? buffer : buffer.slice(buffer.readerIndex(), contentLength);
        if (data == null) {
          dataBuffers.put(id, sliced);
        }
        else if (data instanceof CompositeByteBuf) {
          ((CompositeByteBuf)data).addComponent(sliced);
          data.writerIndex(data.writerIndex() + sliced.readableBytes());
        }
        else {
          if (sliced instanceof CompositeByteBuf) {
            data = ((CompositeByteBuf)sliced).addComponent(0, data);
            data.writerIndex(data.writerIndex() + data.readableBytes());
          }
          else {
            data = context.alloc().compositeBuffer(DEFAULT_MAX_COMPOSITE_BUFFER_COMPONENTS).addComponents(data, sliced);
            data.writerIndex(data.writerIndex() + data.readableBytes() + sliced.readableBytes());
          }
          dataBuffers.put(id, data);
        }
        sliced.retain();
        break;

      case RecordType.STDERR:
        try {
          errorOutputConsumer.consume(buffer.toString(buffer.readerIndex(), contentLength, CharsetUtil.UTF_8));
        }
        catch (Throwable e) {
          LOG.error(e);
        }
        break;

      default:
        LOG.error("Unknown type " + type);
        break;
    }
    return null;
  }
}