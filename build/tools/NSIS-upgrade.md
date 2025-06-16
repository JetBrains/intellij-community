### Upgrading NSIS to a newer version

The NSIS.zip archive contains binaries for all supported platforms, so to upgrade NSIS, one has to download packages,
compile the binaries, get required plugins, and pack everything together.

##### Downloading

1. Go to the [download site](https://sourceforge.net/projects/nsis/files/) and download
   `nsis-VERSION.zip`, `nsis-VERSION-strlen_8192.zip`, and `nsis-VERSION-src.tar.bz2` archives.
2. Unpack the `nsis-VERSION.zip` archive and rename the top-level directory to 'NSIS'.
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
   The resulting binary (`makensis`) is in the current directory; rename them according to the platform conventions
   (`makensis-(mac|linux)-(amd64|aarch64)`).

##### Preparing the combined archive

1. Copy compiled binaries into `NSIS/Bin` directory.
2. From the old NSIS.zip archive, copy the following plugin files into corresponding subdirectories of the 'NSIS' directory:
   - `Include/UAC.nsh`
   - `Plugins/x86-unicode/AccessControl.dll`
   - `Plugins/x86-unicode/ExecDos.dll`
   - `Plugins/x86-unicode/PS.dll`
   - `Plugins/x86-unicode/ShellLink.dll`
   - `Plugins/x86-unicode/UAC.dll`
3. Zip the 'NSIS' directory.
4. Upload to https://jetbrains.team/p/ij/packages/files/intellij-build-dependencies/org/jetbrains/intellij/deps/nsis/.
5. Update the version of 'nsisBuild' in community/build/dependencies/dependencies.properties.

Plugin pages; for reference:
- [Access Control](https://nsis.sourceforge.io/AccessControl_plug-in)
- [ExecDos](https://nsis.sourceforge.io/ExecDos_plug-in)
- [PS](https://nsis.sourceforge.io/PS_plug-in)
- [ShellLink](https://nsis.sourceforge.io/ShellLink_plug-in)
- [UAC](https://nsis.sourceforge.io/UAC_plug-in)
