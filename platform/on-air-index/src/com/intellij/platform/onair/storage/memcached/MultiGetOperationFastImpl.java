// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.platform.onair.storage.memcached;

import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.platform.onair.storage.api.Address;
import com.intellij.platform.onair.tree.ByteUtils;
import net.spy.memcached.KeyUtil;
import net.spy.memcached.MemcachedNode;
import net.spy.memcached.ops.GetOperation;
import net.spy.memcached.ops.KeyedOperation;
import net.spy.memcached.ops.OperationState;
import net.spy.memcached.ops.VBucketAware;
import net.spy.memcached.protocol.binary.OperationImpl;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class MultiGetOperationFastImpl extends OperationImpl implements VBucketAware, KeyedOperation, GetOperation {
  private static final byte CMD_GETQ = 0x09;
  private static final byte CMD_NOOP = 0x0a;
  private static final int EXTRA_HDR_LEN = 4;

  private static final AtomicInteger SEQ_NUMBER;

  private final List<Address> keys;

  private final int terminalOpaque;
  private final int startingOpaque;

  public MultiGetOperationFastImpl(List<Address> keys, AddressOperationCallback cb) {
    super(DUMMY_OPCODE, -1, cb);
    this.keys = keys;
    int size = keys.size();

    final int range = size + 1;
    int finalSequence = SEQ_NUMBER.addAndGet(range);
    while (finalSequence < range) {
      if (finalSequence < 0) {
        SEQ_NUMBER.compareAndSet(finalSequence, 0);
      }
      finalSequence = SEQ_NUMBER.addAndGet(range);
    }

    startingOpaque = finalSequence - size;
    terminalOpaque = finalSequence;
  }

  @Override
  public void initialize() {
    int size = (1 + keys.size()) * MIN_RECV_PACKET;
    for (final Address key : keys) {
      size += key.toString().length(); // TODO: optimize
    }
    // set up the initial header stuff
    ByteBuffer bb = ByteBuffer.allocate(size);
    int i = 0;
    for (final Address key : keys) {
      final byte[] keyBytes = KeyUtil.getKeyBytes(key.toString()); // TODO: optimize
      // Custom header
      bb.put(REQ_MAGIC);
      bb.put(CMD_GETQ);
      bb.putShort((short)keyBytes.length);
      bb.put((byte)0); // extralen
      bb.put((byte)0); // data type
      bb.putShort((short)0); // TODO: set buckets by key?
      bb.putInt(keyBytes.length);
      bb.putInt(startingOpaque + i); // key id
      bb.putLong(0); // cas
      // the actual key
      bb.put(keyBytes);
      i++;
    }
    // Add the noop
    bb.put(REQ_MAGIC);
    bb.put(CMD_NOOP);
    bb.putShort((short)0);
    bb.put((byte)0); // extralen
    bb.put((byte)0); // data type
    bb.putShort((short)0); // reserved
    bb.putInt(0);
    bb.putInt(terminalOpaque);
    bb.putLong(0); // cas

    bb.flip();
    setBuffer(bb);
  }

  @Override
  protected void finishedPayload(byte[] pl) throws IOException {
    getStatusForErrorCode(errorCode, pl);

    if (responseOpaque == terminalOpaque) {
      getCallback().receivedStatus(STATUS_OK);
      transitionState(OperationState.COMPLETE);
    }
    else if (errorCode == ERR_NOT_MY_VBUCKET) {
      throw new UnsupportedOperationException("retry is not supported");
    }
    else if (errorCode != SUCCESS) {
      getLogger().warn("Error on key %s:  %s (%d)", keys.get(responseOpaque),
                       new String(pl, CharsetToolkit.UTF8_CHARSET), errorCode);
    }
    else {
      final int flags = ByteUtils.readUnsignedShort(pl, 0);
      final byte[] data = new byte[pl.length - EXTRA_HDR_LEN];
      System.arraycopy(pl, EXTRA_HDR_LEN, data, 0, pl.length - EXTRA_HDR_LEN);
      AddressOperationCallback cb = (AddressOperationCallback)getCallback();
      cb.gotData(keys.get(responseOpaque - startingOpaque), flags, data);
    }
    resetInput();
  }

  @Override
  protected boolean opaqueIsValid() {
    return responseOpaque <= terminalOpaque && responseOpaque >= startingOpaque;
  }

  public Collection<String> getKeys() {
    return keys.stream().map(address -> address.toString()).collect(Collectors.toList());
  }

  public Collection<MemcachedNode> getNotMyVbucketNodes() {
    return notMyVbucketNodes;
  }

  public void addNotMyVbucketNode(MemcachedNode node) {
    notMyVbucketNodes.add(node);
  }

  public void setNotMyVbucketNodes(Collection<MemcachedNode> nodes) {
    notMyVbucketNodes = nodes;
  }

  @Override
  public void setVBucket(String key, short vbucket) {
    throw new UnsupportedOperationException();
  }

  @Override
  public short getVBucket(String key) {
    return 0;
  }

  static {
    try {
      final Field seqField = OperationImpl.class.getDeclaredField("SEQ_NUMBER");
      seqField.setAccessible(true);
      SEQ_NUMBER = (AtomicInteger)seqField.get(null);
    }
    catch (NoSuchFieldException e) {
      throw new RuntimeException(e);
    }
    catch (IllegalAccessException e) {
      throw new RuntimeException(e);
    }
  }
}
