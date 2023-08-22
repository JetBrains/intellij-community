### Upgrading NSIS to a newer version

The NSIS.zip archive contains both Windows and Linux binaries, so in order to upgrade NSIS, one has to download the former,
compile the latter, and pack both together. **Note**: please make sure all archives are of the same version.

##### [Building](https://documentation.help/NSIS/SectionG.3.html) NSIS compiler on Linux

1. Install [SCons](https://scons.org) (standalone package is enough).
2. Unpack NSIS source archive and `cd` into that directory.
3. Build:
   ```
   scons \
     SKIPSTUBS=all SKIPPLUGINS=all SKIPUTILS=all SKIPMISC=all NSIS_CONFIG_CONST_DATA_PATH=no NSIS_MAX_STRLEN=8192 PREFIX=. \
     install-compiler
   ```
   The resulting binary ('makensis') is in the current directory.

##### Preparing the combined archive

1. [Download](https://sourceforge.net/projects/nsis/files/) and unpack zipped NSIS installation.
2. Rename NSIS top-level directory into 'NSIS' and drop unneeded stuff ('Docs', 'Examples', 'makensisw.exe', 'NSIS.*', 'Plugins/x86-ansi').
3. Download "strlen_8192" archive and unpack it into the 'NSIS' directory (overwrite existing files).
4. Copy compiled Linux binary into 'NSIS/Bin' directory.
5. From the old NSIS.zip archive, copy the following files into corresponding subdirectories of the 'NSIS' directory:
   - 'Include/UAC.nsh'
   - 'Plugins/x86-unicode/AccessControl.dll'
   - 'Plugins/x86-unicode/ExecDos.dll'
   - 'Plugins/x86-unicode/INetC.dll'
   - 'Plugins/x86-unicode/ShellLink.dll'
   - 'Plugins/x86-unicode/UAC.dll'
6. Zip the 'NSIS' directory.
7. Upload to https://jetbrains.team/p/ij/packages/files/intellij-build-dependencies/org/jetbrains/intellij/deps/nsis/.
8. Update the version of 'nsisBuild' in community/build/dependencies/dependencies.properties.
