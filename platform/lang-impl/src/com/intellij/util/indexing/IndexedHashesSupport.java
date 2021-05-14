// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing;

import com.google.common.hash.HashFunction;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.vfs.encoding.EncodingRegistry;
import com.intellij.util.SystemProperties;
import com.intellij.util.indexing.flavor.FileIndexingFlavorProvider;
import com.intellij.util.indexing.flavor.HashBuilder;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

@ApiStatus.Internal
public final class IndexedHashesSupport {
  private static final boolean SKIP_CONTENT_DEPENDENT_CHARSETS =
    SystemProperties.getBooleanProperty("idea.index.hash.skip.content.dependent.charset", true);
  // TODO replace with sha-256
  private static final HashFunction INDEXED_FILE_CONTENT_HASHER = Hashing.sha1();

  public static final int HASH_SIZE_IN_BYTES = INDEXED_FILE_CONTENT_HASHER.bits() / Byte.SIZE;

  public static int getVersion() {
    return 3 + (SKIP_CONTENT_DEPENDENT_CHARSETS ? 1 : 0);
  }

  public static byte @NotNull [] getOrInitIndexedHash(@NotNull FileContentImpl content) {
    byte[] hash = content.getIndexedFileHash();
    if (hash != null) return hash;
    byte[] contentHash = getBinaryContentHash(content.getContent());
    hash = calculateIndexedHash(content, contentHash);
    content.setIndexedFileHash(hash);
    return hash;
  }

  public static byte @NotNull [] getBinaryContentHash(byte @NotNull [] content) {
    return INDEXED_FILE_CONTENT_HASHER.hashBytes(content).asBytes();
  }

  public static byte @NotNull [] calculateIndexedHash(@NotNull IndexedFile indexedFile, byte @NotNull [] contentHash) {
    Hasher hasher = INDEXED_FILE_CONTENT_HASHER.newHasher();
    hasher.putBytes(contentHash);

    if (!FileContentImpl.getFileTypeWithoutSubstitution(indexedFile).isBinary()) {
      // we don't need charset if it depends only on content
      if (!SKIP_CONTENT_DEPENDENT_CHARSETS) {
        Charset charset = getCharsetFromIndexedFile(indexedFile);
        hasher.putString(charset.name(), StandardCharsets.UTF_8);
      }
      else {
        Charset charset = EncodingRegistry.getInstance().getEncoding(indexedFile.getFile(), true);
        if (charset != null) {
          hasher.putString(charset.name(), StandardCharsets.UTF_8);
        }
        else {
          // do nothing; charset is not required and depends only on content
        }
      }
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

  @NotNull
  private static Charset getCharsetFromIndexedFile(@NotNull IndexedFile indexedFile) {
    return indexedFile instanceof FileContentImpl ? ((FileContentImpl)indexedFile).getCharset() : indexedFile.getFile().getCharset();
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