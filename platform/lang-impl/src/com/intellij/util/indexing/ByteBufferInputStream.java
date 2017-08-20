package com.intellij.util.indexing;/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


import java.io.InputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

public class ByteBufferInputStream extends InputStream {

    private ByteBuffer _buf;

    public ByteBufferInputStream(ByteBuffer buf) {
        _buf = buf;
    }

    public void close() {
    }

    public int available() {
        return _buf.remaining();
    }

    public boolean markSupported() {
        return true;
    }

    public void mark(int readlimit) {
        _buf.mark();
    }

    public void reset() {
        _buf.reset();
    }

    public long skip(long n) {
        int nP = Math.min((int)n, _buf.remaining());
        _buf.position(_buf.position() + nP);
        return (long)nP;
    }

    public int read() throws IOException {
        if (!_buf.hasRemaining()) {
            return -1;
        } else {
            return (int) _buf.get() & 0xFF;
        }
    }

    public int read(byte[] bytes, int offset, int length) throws IOException {
        length = Math.min(length, _buf.remaining());
        if (length == 0) {
            return -1;
        } else {
            _buf.get(bytes, offset, length);
            return length;
        }
    }
}
