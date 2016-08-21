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

import org.linguafranca.security.Encryption;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.UUID;

/**
 * This class represents the header portion of a KeePass KDBX file or stream. The header is received in
 * plain text and describes the encryption and compression of the remainder of the file.
 *
 * <p>It is a factory for encryption and decryption streams and contains a hash of its own serialization.
 *
 * <p>While KDBX streams are Little-Endian, data is passed to and from this class in standard Java byte order.
 *
 * @author jo
 */
public class KdbxHeader {

    /**
     * The ordinal 0 represents uncompressed and 1 GZip compressed
     */
    public enum CompressionFlags {
        NONE, GZIP
    }

    /**
     * The ordinals represent various types of encryption that may
     * be applied to fields within the unencrypted data
     *
     * @see StreamFormat
     * @see KdbxStreamFormat
     */
    public enum ProtectedStreamAlgorithm {
        NONE, ARC_FOUR, SALSA_20
    }

    /**
     * This UUID denotes that AES Cipher is in use. No other values are known.
     */
    public static final UUID AES_CIPHER = UUID.fromString("31C1F2E6-BF71-4350-BE58-05216AFC5AFF");

    /* the cipher in use */
    private UUID cipherUuid;
    /* whether the data is compressed */
    private CompressionFlags compressionFlags;
    private byte [] masterSeed;
    private byte[] transformSeed;
    private long transformRounds;
    private byte[] encryptionIv;
    private byte[] protectedStreamKey;
    private ProtectedStreamAlgorithm protectedStreamAlgorithm;
    /* these bytes appear in cipher text immediately following the header */
    private byte[] streamStartBytes;
    /* not transmitted as part of the header, used in the XML payload, so calculated
     * on transmission or receipt */
    private byte[] headerHash;

    /**
     * Construct a default KDBX header
     */
    public KdbxHeader() {
        SecureRandom random = new SecureRandom();
        cipherUuid = AES_CIPHER;
        compressionFlags = CompressionFlags.GZIP;
        masterSeed = random.generateSeed(32);
        transformSeed = random.generateSeed(32);
        transformRounds = 6000;
        encryptionIv = random.generateSeed(16);
        protectedStreamKey = random.generateSeed(32);
        streamStartBytes = new byte[32];
        protectedStreamAlgorithm = ProtectedStreamAlgorithm.SALSA_20;
    }

    /**
     * Create a decrypted input stream using supplied digest and this header
     * apply decryption to the passed encrypted input stream
     *
     * @param digest the key digest
     * @param inputStream the encrypted input stream
     * @return a decrypted stream
     * @throws IOException
     */
    public InputStream createDecryptedStream(byte[] digest, InputStream inputStream) throws IOException {
        byte[] finalKeyDigest = Encryption.getFinalKeyDigest(digest, getMasterSeed(), getTransformSeed(), getTransformRounds());
        return Encryption.getDecryptedInputStream(inputStream, finalKeyDigest, getEncryptionIv());
    }

    /**
     * Create an unencrypted outputstream using the supplied digest and this header
     * and use the supplied output stream to write encrypted data.
     * @param digest the key digest
     * @param outputStream the output stream which is the destination for encrypted data
     * @return an output stream to write unencrypted data to
     * @throws IOException
     */
    public OutputStream createEncryptedStream(byte[] digest, OutputStream outputStream) throws IOException {
        byte[] finalKeyDigest = Encryption.getFinalKeyDigest(digest, getMasterSeed(), getTransformSeed(), getTransformRounds());
        return Encryption.getEncryptedOutputStream(outputStream, finalKeyDigest, getEncryptionIv());
    }

    public UUID getCipherUuid() {
        return cipherUuid;
    }

    public CompressionFlags getCompressionFlags() {
        return compressionFlags;
    }

    public byte[] getMasterSeed() {
        return masterSeed;
    }

    public byte[] getTransformSeed() {
        return transformSeed;
    }

    public long getTransformRounds() {
        return transformRounds;
    }

    public byte[] getEncryptionIv() {
        return encryptionIv;
    }

    public byte[] getProtectedStreamKey() {
        return protectedStreamKey;
    }

    public byte[] getStreamStartBytes() {
        return streamStartBytes;
    }

    public ProtectedStreamAlgorithm getProtectedStreamAlgorithm() {
        return protectedStreamAlgorithm;
    }

    public byte[] getHeaderHash() {
        return headerHash;
    }

    public void setCipherUuid(byte[] uuid) {
        ByteBuffer b = ByteBuffer.wrap(uuid);
        UUID incoming = new UUID(b.getLong(), b.getLong(8));
        if (!incoming.equals(AES_CIPHER)) {
            throw new IllegalStateException("Unknown Cipher UUID " + incoming.toString());
        }
        this.cipherUuid = incoming;
    }

    public void setCompressionFlags(int flags) {
        this.compressionFlags = CompressionFlags.values()[flags];
    }

    public void setMasterSeed(byte[] masterSeed) {
        this.masterSeed = masterSeed;
    }

    public void setTransformSeed(byte[] transformSeed) {
        this.transformSeed = transformSeed;
    }

    public void setTransformRounds(long transformRounds) {
        this.transformRounds = transformRounds;
    }

    public void setEncryptionIv(byte[] encryptionIv) {
        this.encryptionIv = encryptionIv;
    }

    public void setProtectedStreamKey(byte[] protectedStreamKey) {
        this.protectedStreamKey = protectedStreamKey;
    }

    public void setStreamStartBytes(byte[] streamStartBytes) {
        this.streamStartBytes = streamStartBytes;
    }

    public void setInnerRandomStreamId(int innerRandomStreamId) {
        this.protectedStreamAlgorithm = ProtectedStreamAlgorithm.values()[innerRandomStreamId];
    }

    public void setHeaderHash(byte[] headerHash) {
        this.headerHash = headerHash;
    }
}
