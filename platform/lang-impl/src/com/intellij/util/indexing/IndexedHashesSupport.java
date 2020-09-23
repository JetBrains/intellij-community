// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing;

import com.google.common.hash.HashFunction;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.vfs.newvfs.persistent.FSRecords;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFSImpl;
import com.intellij.util.indexing.flavor.FileIndexingFlavorProvider;
import com.intellij.util.indexing.flavor.HashBuilder;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@ApiStatus.Internal
public final class IndexedHashesSupport {
  // TODO replace with sha-256
  private static final HashFunction INDEXED_FILE_CONTENT_HASHER = Hashing.sha1();

  public static int getVersion() {
    return 3;
  }

  public static byte @NotNull [] getOrInitIndexedHash(@NotNull FileContentImpl content) {
    byte[] hash = content.getHash();
    if (hash != null) return hash;

    byte[] contentHash = PersistentFSImpl.getContentHashIfStored(content.getFile());
    if (contentHash == null) {
      contentHash = getBinaryContentHash(content.getContent());
      // todo store content hash in FS
    }

    hash = calculateIndexedHash(content, contentHash, false);
    content.setHashes(hash);
    return hash;
  }

  public static byte @NotNull [] getBinaryContentHash(byte @NotNull [] content) {
    //TODO: duplicate of com.intellij.openapi.vfs.newvfs.persistent.FSRecords.calculateHash
    MessageDigest digest = FSRecords.getContentHashDigest();
    digest.update(String.valueOf(content.length).getBytes(StandardCharsets.UTF_8));
    digest.update("\u0000".getBytes(StandardCharsets.UTF_8));
    digest.update(content);
    return digest.digest();
  }

  public static byte @NotNull [] calculateIndexedHash(@NotNull IndexedFile indexedFile, byte @NotNull [] contentHash, boolean isUtf8Forced) {
    Hasher hasher = INDEXED_FILE_CONTENT_HASHER.newHasher();
    hasher.putBytes(contentHash);

    if (!FileContentImpl.getFileTypeWithoutSubstitution(indexedFile).isBinary()) {
      Charset charset = isUtf8Forced ? StandardCharsets.UTF_8 :
                        indexedFile instanceof FileContentImpl
                        ? ((FileContentImpl)indexedFile).getCharset()
                        : indexedFile.getFile().getCharset();
      hasher.putString(charset.name(), StandardCharsets.UTF_8);
    }

    hasher.putString(indexedFile.getFileName(), StandardCharsets.UTF_8);

    FileType fileType = indexedFile.getFileType();
    hasher.putString(fileType.getName(), StandardCharsets.UTF_8);

    @Nullable
    FileIndexingFlavorProvider<?> provider = FileIndexingFlavorProvider.INSTANCE.forFileType(fileType);
    if (provider != null) {
      buildFlavorHash(indexedFile, provider, new HashBuilder() {
        @Override
        public @NotNull HashBuilder putInt(int val) {
          hasher.putInt(val);
          return this;
        }

        @Override
        public @NotNull HashBuilder putBoolean(boolean val) {
          hasher.putBoolean(val);
          return this;
        }

        @Override
        public @NotNull HashBuilder putString(@NotNull CharSequence charSequence) {
          hasher.putString(charSequence, StandardCharsets.UTF_8);
          return this;
        }
      });
    }

    return hasher.hash().asBytes();
  }

  private static <F> void buildFlavorHash(@NotNull IndexedFile indexedFile,
                                          @NotNull FileIndexingFlavorProvider<F> flavorProvider,
                                          @NotNull HashBuilder hashBuilder) {
    F flavor = flavorProvider.getFlavor(indexedFile);
    hashBuilder.putString(flavorProvider.getId());
    hashBuilder.putInt(flavorProvider.getVersion());
    if (flavor != null) {
      flavorProvider.buildHash(flavor, hashBuilder);
    }
  }
}