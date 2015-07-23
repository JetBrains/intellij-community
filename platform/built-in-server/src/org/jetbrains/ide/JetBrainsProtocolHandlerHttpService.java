package org.jetbrains.ide;

import com.google.gson.stream.JsonReader;
import com.intellij.openapi.application.JetBrainsProtocolHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.QueryStringDecoder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

/**
 * @author Konstantin Bulenkov
 */
public class JetBrainsProtocolHandlerHttpService extends RestService {
  @NotNull
  @Override
  protected String getServiceName() {
    return "internal";
  }

  @Override
  protected boolean isMethodSupported(@NotNull HttpMethod method) {
    return method == HttpMethod.POST;
  }

  @Nullable
  @Override
  public String execute(@NotNull QueryStringDecoder urlDecoder, @NotNull FullHttpRequest request, @NotNull ChannelHandlerContext context) throws IOException {
    final JsonReader reader = createJsonReader(request);
    reader.beginObject();
    final String name = reader.nextName();
    final String url = reader.nextString();
    reader.endObject();
    if ("url".equals(name) && url != null && url.startsWith(JetBrainsProtocolHandler.PROTOCOL)) {
      JetBrainsProtocolHandler.processJetBrainsLauncherParameters(url);
    }
    sendOk(request, context);
    return null;
  }
}
