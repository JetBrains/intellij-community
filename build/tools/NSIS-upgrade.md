### Upgrading NSIS to a newer version

The NSIS.zip archive contains binaries for all supported platforms, so to upgrade NSIS, one has to download packages,
compile the binaries, get required plugins, and pack everything together.

There are two variants of the package. The steps below apply to both; only the download site, the upload path, and
the version property differ, so pick the ones for the variant you are upgrading. Both are in use, so upgrade them
together.

| Variant | Version property | Used when |
|---|---|---|
| NSIS (stock) | `nsisBuild` | by default |
| NSISBI — a fork with a 64-bit compiler, for installers over ~2 GB | `nsisbiBuild` | a product sets `WindowsDistributionCustomizer.useBigNsisInstaller` |

##### Downloading

1. Go to the download site — [NSIS][nsis-dl] or [NSISBI][nsisbi-dl] — and download three archives:
   - NSIS: `nsis-VERSION.zip`, `nsis-VERSION-strlen_8192.zip`, `nsis-VERSION-src.tar.bz2`
   - NSISBI: `nsisbi-VERSION-amd64.zip`, `nsisbi-VERSION-strlen_8192-amd64.zip`, `nsisbi-VERSION-src.tar.bz2`
2. Unpack the first archive and rename the top-level directory to 'NSIS'.
3. Delete unneeded stuff (`Docs`, `Examples`, `makensisw.exe`, `NSIS.*`, `Plugins/x86-ansi`).
4. Unpack the "strlen_8192" archive into the 'NSIS' directory (overwrite existing files).

##### [Building](https://documentation.help/NSIS/SectionG.3.html) NSIS compiler on Linux/macOS

1. Install [SCons](https://scons.org) (standalone package is enough) and build dependencies (gcc/g++, zlib-dev).
2. Unpack NSIS source archive and `cd` into that directory.
3. Build:
   ```
   scons \
     SKIPSTUBS=all SKIPPLUGINS=all SKIPUTILS=all SKIPMISC=all NSIS_CONFIG_CONST_DATA_PATH=no NSIS_MAX_STRLEN=8192 PREFIX=. \
     install-compiler
   ```
   Build on a system whose glibc is not newer than the build agents' (currently 2.35, Ubuntu 22.04). The binaries
   work with a newer glibc but not with an older one, so a compiler built on a newer distribution does not start on
   the agents. The easiest way is a container, e.g.
   `docker run --rm -v "$PWD:/work" -w /work debian:11-slim` (glibc 2.31).

   Cross-compiling (e.g. aarch64 on an x64 host) needs one edit in the sources. The build detects endianness by
   compiling and running a small test program, which cannot work for a foreign architecture, so it falls back to
   big-endian. In `SCons/Config/gnu`, replace

   ```
   result = not ctx.TryRun(test, '.c')[0]
   ```

   with `result = False`, as all NSIS targets are little-endian.

   The resulting binary (`makensis`) is in the current directory; rename them according to the platform conventions
   (`makensis-(mac|linux)-(amd64|aarch64)`).

##### Preparing the combined archive

1. Copy compiled binaries into `NSIS/Bin` directory.
2. From the previous version of the archive, copy the following plugin files into corresponding subdirectories of the 'NSIS' directory:
   - `Include/UAC.nsh`
   - `Plugins/x86-unicode/AccessControl.dll`
   - `Plugins/x86-unicode/ExecDos.dll`
   - `Plugins/x86-unicode/PS.dll`
   - `Plugins/x86-unicode/ShellLink.dll`
   - `Plugins/x86-unicode/UAC.dll`
3. For NSISBI, add a `Target x86-unicode` line at the top of `NSIS/nsisconf.nsh` (makensis includes this file before
   every script). Without it, building an installer fails with `Plugin not found, cannot call
   AccessControl::GrantOnFile`, because NSISBI's `makensis.exe` builds an amd64 installer by default and so looks for
   the plugins in `Plugins/amd64-unicode`, while the files from step 2 are x86.
4. Zip the 'NSIS' directory — its name stays 'NSIS' for both variants, the build looks for exactly that inside the
   archive. Name the archive after the variant: `NSIS-VERSION.zip` or `NSISBI-VERSION.zip`.
5. Upload it to the build dependencies repository — [deps/nsis][nsis-upload] or [deps/nsisbi][nsisbi-upload].
6. Update the version of 'nsisBuild' ('nsisbiBuild' for NSISBI) in community/build/dependencies/dependencies.properties.

Plugin pages; for reference:
- [Access Control](https://nsis.sourceforge.io/AccessControl_plug-in)
- [ExecDos](https://nsis.sourceforge.io/ExecDos_plug-in)
- [PS](https://nsis.sourceforge.io/PS_plug-in)
- [ShellLink](https://nsis.sourceforge.io/ShellLink_plug-in)
- [UAC](https://nsis.sourceforge.io/UAC_plug-in)

[nsis-dl]: https://sourceforge.net/projects/nsis/files/
[nsisbi-dl]: https://sourceforge.net/projects/nsisbi/files/
[nsis-upload]: https://jetbrains.team/p/ij/packages/files/intellij-build-dependencies/org/jetbrains/intellij/deps/nsis/
[nsisbi-upload]: https://jetbrains.team/p/ij/packages/files/intellij-build-dependencies/org/jetbrains/intellij/deps/nsisbi/
