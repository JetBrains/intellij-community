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

/** The cache keeps up to five recently used binaries on a best-effort basis. Each session runs its own temporary copy. */
internal class IjentBinaryCache(val hash: String) {
  internal data class PosixCommands(
    val mkdir: String,
    val mv: String,
    val checksum: String,
    val touch: String,
    val ls: String,
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
            $$touch -cm "$CACHED_BINARY" 1>&2 || :;
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
          $$mv -f "$CACHE_UPLOAD" "$CACHED_BINARY" &&
          (
            cd "$CACHE_DIR" || exit;
            set --;
            for CACHE_ENTRY in ijent-*; do
              CACHE_HASH=${CACHE_ENTRY#ijent-};
              case "$CACHE_HASH" in ''|*[!0123456789abcdef]*) continue;; esac;
              if [ "${#CACHE_HASH}" -eq 64 ] && [ -f "$CACHE_ENTRY" ] && [ ! -L "$CACHE_ENTRY" ]; then
                set -- "$@" "$CACHE_ENTRY";
              fi;
            done;
            if [ "$#" -gt $$MAX_ENTRIES ]; then
              CACHE_ENTRIES=$(unset CLICOLOR CLICOLOR_FORCE; LC_ALL=C $$ls -1td "$@") || exit;
              CACHE_REMAINING=$${MAX_ENTRIES - 1};
              printf '%s\n' "$CACHE_ENTRIES" | while IFS= read -r CACHE_ENTRY; do
                if [ "$CACHE_ENTRY" = 'ijent-$$hash' ]; then
                  continue;
                elif [ "$CACHE_REMAINING" -gt 0 ]; then
                  CACHE_REMAINING=$((CACHE_REMAINING - 1));
                elif [ -f "$CACHE_ENTRY" ] && [ ! -L "$CACHE_ENTRY" ]; then
                  $$rm -f "$CACHE_ENTRY" || :;
                fi;
              done;
            fi;
          )
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
            [IO.File]::SetLastWriteTimeUtc($ijentCachedBinary, [DateTime]::UtcNow);
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
          [IO.File]::SetLastWriteTimeUtc($ijentCachedBinary, [DateTime]::UtcNow);
          Get-ChildItem -LiteralPath $ijentCacheDir -File | Where-Object {
            $_.Name -cmatch '\Aijent-[0-9a-f]{64}\.exe\z' -and $_.Name -cne 'ijent-$$hash.exe' -and
            ($_.Attributes -band [IO.FileAttributes]::ReparsePoint) -eq 0
          } | Sort-Object -Property LastWriteTimeUtc, Name -Descending | Select-Object -Skip $${MAX_ENTRIES - 1} | ForEach-Object {
            try { [IO.File]::Delete($_.FullName) } catch { [Console]::Error.WriteLine($_.Exception.Message) }
          };
        }
      } catch { [Console]::Error.WriteLine($_.Exception.Message) }
      finally { Remove-Item -LiteralPath $ijentCacheUpload -Force -ErrorAction SilentlyContinue }
    }
  """.trimIndent().replace('\n', ' ')

  companion object {
    private const val MAX_ENTRIES = 5

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
