// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent.spi

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.HexFormat

/** The cache stores exact copies of the selected binary. Each session runs its own temporary copy. */
internal class IjentBinaryCache(val hash: String) {
  internal data class PosixCommands(
    val mkdir: String,
    val mv: String,
    val checksum: String,
  )

  fun posixRestore(context: DeployingContext): String = context.run {
    val commands = requireNotNull(cacheCommands)
    with(commands) {
      $$"""
      CACHE_DIR='';
      if [ -n "$HOME" ]; then
        CACHE_DIR="$HOME/.cache/JetBrains/ijent";
        if (umask 077; $$mkdir -p "$CACHE_DIR") && [ ! -L "$CACHE_DIR" ] && [ -O "$CACHE_DIR" ] && $$chmod 700 "$CACHE_DIR"; then
          CACHED_BINARY="$CACHE_DIR/ijent-$$hash";
          if [ -f "$CACHED_BINARY" ] && [ ! -L "$CACHED_BINARY" ] && $$cp "$CACHED_BINARY" "$BINARY" &&
             [ "$($$checksum "$BINARY" | $$cut -d ' ' -f1)" = '$$hash' ] && $$chmod 500 "$BINARY"; then
            echo "$BINARY";
          else
            $$rm -f "$BINARY";
          fi;
        else
          CACHE_DIR='';
        fi;
      fi
      """.trimIndent()
    }
  }

  fun posixPublish(context: DeployingContext): String = context.run {
    val commands = requireNotNull(cacheCommands)
    with(commands) {
      $$"""
      if [ -n "$CACHE_DIR" ]; then
        (
          CACHE_UPLOAD=$($$mktemp "$CACHE_DIR/.upload-XXXXXXXXXX") &&
          trap '$$rm -f "$CACHE_UPLOAD"' 0 &&
          $$cp "$BINARY" "$CACHE_UPLOAD" &&
          [ "$($$checksum "$CACHE_UPLOAD" | $$cut -d ' ' -f1)" = '$$hash' ] &&
          $$chmod 500 "$CACHE_UPLOAD" &&
          $$mv -f "$CACHE_UPLOAD" "$CACHED_BINARY"
        ) 1>&2 || :;
      fi
      """.trimIndent()
    }
  }

  fun powerShellRestore(): String = $$"""
    $ijentCacheDir = $null;
    try {
      $ijentCacheRoot = [Environment]::GetFolderPath('LocalApplicationData');
      if ($ijentCacheRoot) {
        $ijentCacheDir = Join-Path $ijentCacheRoot 'JetBrains/ijent';
        [IO.Directory]::CreateDirectory($ijentCacheDir) | Out-Null;
        $ijentCachedBinary = Join-Path $ijentCacheDir 'ijent-$$hash.exe';
        if ([IO.File]::Exists($ijentCachedBinary)) {
          [IO.File]::Copy($ijentCachedBinary, $ijentBinary, $true);
          if ((Get-FileHash -LiteralPath $ijentBinary -Algorithm SHA256).Hash -eq '$$hash') {
            Write-Output $ijentBinary;
          }
        }
      }
    } catch { [Console]::Error.WriteLine($_.Exception.Message) }
  """.trimIndent().replace('\n', ' ')

  fun powerShellPublish(): String = $$"""
    if ($ijentCacheDir) {
      $ijentCacheUpload = Join-Path $ijentCacheDir ('.upload-' + [Guid]::NewGuid().ToString('N'));
      try {
        [IO.File]::Copy($ijentBinary, $ijentCacheUpload);
        if ((Get-FileHash -LiteralPath $ijentCacheUpload -Algorithm SHA256).Hash -eq '$$hash') {
          Move-Item -LiteralPath $ijentCacheUpload -Destination $ijentCachedBinary -Force;
        }
      } catch { [Console]::Error.WriteLine($_.Exception.Message) }
      finally { Remove-Item -LiteralPath $ijentCacheUpload -Force -ErrorAction SilentlyContinue }
    }
  """.trimIndent().replace('\n', ' ')

  companion object {
    suspend fun forBinary(binary: Path): IjentBinaryCache {
      val hash = withContext(Dispatchers.IO) {
        val digest = MessageDigest.getInstance("SHA-256")
        DigestInputStream(Files.newInputStream(binary), digest).use { it.transferTo(OutputStream.nullOutputStream()) }
        HexFormat.of().formatHex(digest.digest())
      }
      return IjentBinaryCache(hash)
    }
  }
}
