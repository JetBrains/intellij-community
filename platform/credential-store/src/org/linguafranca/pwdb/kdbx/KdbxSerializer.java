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

import com.google.common.io.LittleEndianDataInputStream;
import com.google.common.io.LittleEndianDataOutputStream;
import com.intellij.credentialStore.kdbx.KeePassCredentials;
import org.linguafranca.hashedblock.HashedBlockInputStream;
import org.linguafranca.hashedblock.HashedBlockOutputStream;
import org.linguafranca.security.Encryption;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.security.DigestInputStream;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * This class provides static methods for the encryption and decryption of Keepass KDBX files.
 * <p/>
 * A KDBX file is little-endian and consists of the following:
 * <ol>
 *     <li>An unencrypted portion</li>
 * <ol>
 *     <li>8 bytes Magic number</li>
 *     <li>4 bytes version</li>
 *     <li>A header containing details of the encryption of the remainder of the file</li>
 *     <p>The header fields are encoded using a TLV style. The Type is an enumeratrion encoded in 1 byte.
 *     The length is encoded in 2 bytes and the value according to the length denoted. The sequence is
 *     terminated by a zero type with 0 length.</p>
 * </ol>
 *  <li>An encrypted portion</li>
 *  <ol>
 *      <li>A sequence of bytes contained in the header. If they don't match, decryption has not worked.</li>
 *      <li>A payload serialized in Hashed Block format.</li>
 *      <p>The content of this payload is expected to be a Keepass Database in XML format.</p>
 *  </ol>
 * </ol>
 * <p/>
 * The methods in this class provide support for serializing and deserializing plain text payload content
 * to and from the above format.
 * <p/>
 * @author jo
 */
public class KdbxSerializer {

    // make entirely static
    private KdbxSerializer() {}

    /**
     * Provides the payload of a KDBX file as an unencrypted {@link InputStream}.
     * @param credentials credentials for decryption of the stream
     * @param kdbxHeader a header instance to be populated with values from the stream
     * @param inputStream a KDBX formatted input stream
     * @return an unencrypted input stream, to be read and closed by the caller
     * @throws IOException
     */
    public static InputStream createUnencryptedInputStream(KeePassCredentials credentials, KdbxHeader kdbxHeader, InputStream inputStream) throws IOException {

        readKdbxHeader(kdbxHeader, inputStream);

        InputStream decryptedInputStream = kdbxHeader.createDecryptedStream(credentials.getKey(), inputStream);

        checkStartBytes(kdbxHeader, decryptedInputStream);

        HashedBlockInputStream blockInputStream = new HashedBlockInputStream(decryptedInputStream, true);

        if (kdbxHeader.getCompressionFlags().equals(KdbxHeader.CompressionFlags.NONE)) {
            return blockInputStream;
        }
        return new GZIPInputStream(blockInputStream);
    }

    /**
     * Provides an {@link OutputStream} to be encoded and encrypted in KDBX format
     * @param credentials credentials for encryption of the stream
     * @param kdbxHeader a KDBX header to control the formatting and encryption operation
     * @param outputStream output stream to contain the KDBX formatted output
     * @return an unencrypted output stream, to be written to, flushed and closed by the caller
     * @throws IOException
     */
    public static OutputStream createEncryptedOutputStream(KeePassCredentials credentials, KdbxHeader kdbxHeader, OutputStream outputStream) throws IOException {

        writeKdbxHeader(kdbxHeader, outputStream);

        OutputStream encryptedOutputStream = kdbxHeader.createEncryptedStream(credentials.getKey(), outputStream);

        writeStartBytes(kdbxHeader, encryptedOutputStream);

        HashedBlockOutputStream blockOutputStream = new HashedBlockOutputStream(encryptedOutputStream, true);

        if(kdbxHeader.getCompressionFlags().equals(KdbxHeader.CompressionFlags.NONE)) {
            return blockOutputStream;
        }
        return new GZIPOutputStream(blockOutputStream);
    }


    private static void checkStartBytes(KdbxHeader kdbxHeader, InputStream decryptedInputStream) throws IOException {
        LittleEndianDataInputStream ledis = new LittleEndianDataInputStream(decryptedInputStream);

        byte [] startBytes = new byte[32];
        ledis.readFully(startBytes);
        if (!Arrays.equals(startBytes, kdbxHeader.getStreamStartBytes())) {
            throw new IllegalStateException("Inconsistent stream bytes");
        }
    }

    private static void writeStartBytes(KdbxHeader kdbxHeader, OutputStream encryptedOutputStream) throws IOException {
        LittleEndianDataOutputStream ledos = new LittleEndianDataOutputStream(encryptedOutputStream);
        ledos.write(kdbxHeader.getStreamStartBytes());
    }

    private static final int SIG1 = 0x9AA2D903;
    private static final int SIG2 = 0xB54BFB67;
    private static final int FILE_VERSION_CRITICAL_MASK = 0xFFFF0000;
    private static final int FILE_VERSION_32 = 0x00030001;

    private static class HeaderType {
        static final byte END = 0;
        static final byte COMMENT = 1;
        static final byte CIPHER_ID = 2;
        static final byte COMPRESSION_FLAGS = 3;
        static final byte MASTER_SEED = 4;
        static final byte TRANSFORM_SEED = 5;
        static final byte TRANSFORM_ROUNDS = 6;
        static final byte ENCRYPTION_IV = 7;
        static final byte PROTECTED_STREAM_KEY = 8;
        static final byte STREAM_START_BYTES = 9;
        static final byte INNER_RANDOM_STREAM_ID = 10;
    }
    
    /**
     * Read two lots of 4 bytes and verify that they satisfy the signature of a
     * kdbx file;
     * @param ledis an input stream
     * @return true if it looks like this is a kdbx file
     * @throws IOException
     */
    private static boolean verifyMagicNumber(LittleEndianDataInputStream ledis) throws IOException {
        int sig1 = ledis.readInt();
        int sig2 = ledis.readInt();
        return sig1 == SIG1 && sig2 == SIG2;
    }

    /**
     * Read 4 bytes and make sure they conform to expectations of file version
     * @param ledis an input stream
     * @return true if it looks like we understand this file version
     * @throws IOException
     */
    private static boolean verifyFileVersion(LittleEndianDataInputStream ledis) throws IOException {
        return ((ledis.readInt() & FILE_VERSION_CRITICAL_MASK) <= (FILE_VERSION_32 & FILE_VERSION_CRITICAL_MASK));
    }

    /**
     * Populate a KdbxHeader from the input stream supplied
     * @param kdbxHeader a header to be populated
     * @param inputStream an input stream
     * @return the populated KdbxHeader
     * @throws IOException
     */
    public static KdbxHeader readKdbxHeader(KdbxHeader kdbxHeader, InputStream inputStream) throws IOException {

        MessageDigest digest = Encryption.getMessageDigestInstance();
        // we do not close this stream, otherwise we lose our place in the underlying stream
        DigestInputStream digestInputStream = new DigestInputStream(inputStream, digest);
        // we do not close this stream, otherwise we lose our place in the underlying stream
        LittleEndianDataInputStream ledis = new LittleEndianDataInputStream(digestInputStream);

        if (!verifyMagicNumber(ledis)) {
            throw new IllegalStateException("Magic number did not match");
        }

        if (!verifyFileVersion(ledis)) {
            throw new IllegalStateException("File version did not match");
        }
        
        byte headerType;
        while ((headerType = ledis.readByte()) != HeaderType.END) {
            switch (headerType) {

                case HeaderType.COMMENT:
                    getByteArray(ledis);
                    break;

                case HeaderType.CIPHER_ID:
                    kdbxHeader.setCipherUuid(getByteArray(ledis));
                    break;

                case HeaderType.COMPRESSION_FLAGS:
                    kdbxHeader.setCompressionFlags(getInt(ledis));
                    break;

                case HeaderType.MASTER_SEED:
                    kdbxHeader.setMasterSeed(getByteArray(ledis));
                    break;

                case HeaderType.TRANSFORM_SEED:
                    kdbxHeader.setTransformSeed(getByteArray(ledis));
                    break;

                case HeaderType.TRANSFORM_ROUNDS:
                    kdbxHeader.setTransformRounds(getLong(ledis));
                    break;

                case HeaderType.ENCRYPTION_IV:
                    kdbxHeader.setEncryptionIv(getByteArray(ledis));
                    break;

                case HeaderType.PROTECTED_STREAM_KEY:
                    kdbxHeader.setProtectedStreamKey(getByteArray(ledis));
                    break;

                case HeaderType.STREAM_START_BYTES:
                    kdbxHeader.setStreamStartBytes(getByteArray(ledis));
                    break;

                case HeaderType.INNER_RANDOM_STREAM_ID:
                    kdbxHeader.setInnerRandomStreamId(getInt(ledis));
                    break;

                default: throw new IllegalStateException("Unknown File Header");
            }
        }

        // consume length etc. following END flag
        getByteArray(ledis);

        kdbxHeader.setHeaderHash(digest.digest());
        return kdbxHeader;
    }

    /**
     * Write a KdbxHeader to the output stream supplied. The header is updated with the
     * message digest of the written stream.
     * @param kdbxHeader the header to write and update
     * @param outputStream the output stream
     * @throws IOException
     */
    public static void writeKdbxHeader(KdbxHeader kdbxHeader, OutputStream outputStream) throws IOException {
        MessageDigest messageDigest = Encryption.getMessageDigestInstance();
        DigestOutputStream digestOutputStream = new DigestOutputStream(outputStream, messageDigest);
        LittleEndianDataOutputStream ledos = new LittleEndianDataOutputStream(digestOutputStream);

        // write the magic number
        ledos.writeInt(SIG1);
        ledos.writeInt(SIG2);
        // write a file version
        ledos.writeInt(FILE_VERSION_32);

        ledos.writeByte(HeaderType.CIPHER_ID);
        ledos.writeShort(16);
        byte[] b = new byte[16];
        ByteBuffer bb = ByteBuffer.wrap(b);
        bb.putLong(kdbxHeader.getCipherUuid().getMostSignificantBits());
        bb.putLong(8, kdbxHeader.getCipherUuid().getLeastSignificantBits());
        ledos.write(b);

        ledos.writeByte(HeaderType.COMPRESSION_FLAGS);
        ledos.writeShort(4);
        ledos.writeInt(kdbxHeader.getCompressionFlags().ordinal());

        ledos.writeByte(HeaderType.MASTER_SEED);
        ledos.writeShort(kdbxHeader.getMasterSeed().length);
        ledos.write(kdbxHeader.getMasterSeed());

        ledos.writeByte(HeaderType.TRANSFORM_SEED);
        ledos.writeShort(kdbxHeader.getTransformSeed().length);
        ledos.write(kdbxHeader.getTransformSeed());

        ledos.writeByte(HeaderType.TRANSFORM_ROUNDS);
        ledos.writeShort(8);
        ledos.writeLong(kdbxHeader.getTransformRounds());

        ledos.writeByte(HeaderType.ENCRYPTION_IV);
        ledos.writeShort(kdbxHeader.getEncryptionIv().length);
        ledos.write(kdbxHeader.getEncryptionIv());

        ledos.writeByte(HeaderType.PROTECTED_STREAM_KEY);
        ledos.writeShort(kdbxHeader.getProtectedStreamKey().length);
        ledos.write(kdbxHeader.getProtectedStreamKey());

        ledos.writeByte(HeaderType.STREAM_START_BYTES);
        ledos.writeShort(kdbxHeader.getStreamStartBytes().length);
        ledos.write(kdbxHeader.getStreamStartBytes());

        ledos.writeByte(HeaderType.INNER_RANDOM_STREAM_ID);
        ledos.writeShort(4);
        ledos.writeInt(kdbxHeader.getProtectedStreamAlgorithm().ordinal());

        ledos.writeByte(HeaderType.END);
        ledos.writeShort(0);

        MessageDigest digest = digestOutputStream.getMessageDigest();
        kdbxHeader.setHeaderHash(digest.digest());
    }


    private static int getInt(LittleEndianDataInputStream ledis) throws IOException {
        short fieldLength = ledis.readShort();
        if (fieldLength != 4) {
            throw new IllegalStateException("Int required but length was " + fieldLength);
        }
        return ledis.readInt();
    }

    private static long getLong(LittleEndianDataInputStream ledis) throws IOException {
        short fieldLength = ledis.readShort();
        if (fieldLength != 8) {
            throw new IllegalStateException("Long required but length was " + fieldLength);
        }
        return ledis.readLong();
    }

    private static byte [] getByteArray(LittleEndianDataInputStream ledis) throws IOException {
        short fieldLength = ledis.readShort();
        byte [] value = new byte[fieldLength];
        ledis.readFully(value);
        return value;
    }
}
