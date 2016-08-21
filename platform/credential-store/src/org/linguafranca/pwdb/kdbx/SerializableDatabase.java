/*
 * Copyright 2015 Jo Rabin
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.linguafranca.pwdb.kdbx;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * This interface allows for serialization and deserialization of KDBX databases.
 *
 * <p>Databases instantiate themselves from a stream and serialize to a stream,
 * and need to be able to encrypt and decrypt data (e.g. Protected fields in KDBX format).
 *
 * <p>KDBX databases contain a header hash (i.e. a hash of the contents of
 * some portion of the {@link StreamFormat} they have been loaded from or saved to.
 * Which means that databases must support the setting of this value after the header
 * has been written on save, and reading the value after load to allow for integrity checking.
 *
 * @author jo
 */
public interface SerializableDatabase {

    interface Encryption {
        byte[] getKey();

        byte[] decrypt(byte[] encryptedText);

        byte[] encrypt(byte[] decryptedText);
    }

    SerializableDatabase load(InputStream inputStream) throws IOException;

    void save(OutputStream outputStream) throws IOException;

    Encryption getEncryption();

    void setEncryption(Encryption encryption);

    byte[] getHeaderHash();

    void setHeaderHash(byte[] hash);
}
