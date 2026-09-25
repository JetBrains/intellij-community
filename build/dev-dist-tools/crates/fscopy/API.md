# fscopy

The copy and path helpers of the dev-distribution tools. All functions return `io::Result`. An error message names the
paths, and the `io::ErrorKind` of the cause stays the same.

Every copy goes through `clone_or_copy`, which uses `std::fs::copy`. On macOS it clones the file with `fclonefileat` on
APFS. On Linux it uses `copy_file_range`, which clones on Btrfs and XFS. On Windows it uses `CopyFileExW`, which
clones on ReFS and a Dev Drive.

## Differences from the Go original

- On macOS, a copy also gets the extended attributes and the ACL of the source, because the clone and `fcopyfile`
  copy them.
- On macOS, `real_path` and `resolve_links` return each name in the case that the file system stores. The Go original
  kept the case of the input.
- On Windows, `resolve_links` removes a `..` lexically before it follows a link. The Go original went to the parent of
  the link target.
- `create_dirs_0755` gives each new directory the mode 0755. Go `os.MkdirAll(path, 0o755)` applied the umask, so a
  umask such as 027 or 077 gave a different mode.
- On Windows, `symlink` takes the link kind from `target_is_directory`. Go `os.Symlink` read the kind from the target
  on the disk, so a missing target gave a file link.

## Public items

| Item | Go original | Description |
|---|---|---|
| `clone_or_copy(&Path, &Path)` | `project-model-tree` `copyFile` | Fails with `AlreadyExists` when the destination exists, also for a dangling link. Follows a link at the source. Keeps the permission bits of the source. |
| `copy_with_attributes(&Path, &Path)` | composer `copyWithAttributes` | Java `Files.copy(COPY_ATTRIBUTES)` for a regular file: the clone keeps the permission bits, then the function sets the modification time. Refuses a directory, every other file type, and a mode with a setuid, setgid or sticky bit, with `InvalidInput`. The composer copies only regular files. |
| `copy_with_mode(&Path, &Path, executable: bool, mode: Option<u32>)` | collector `copyLocalFile` | Clones, then sets `mode & 0o777`, or 0755 or 0644 from `executable`. On Windows it sets only the read-only attribute from the owner write bit. |
| `replace_with_copy(&Path, &Path)` | pluginpack `copyFile` | Replaces an existing regular file (a reserved output) with a clone of the source. Fails with `NotFound` when the destination does not exist. The caller sets the mode after. |
| `set_distribution_file_mode(&Path, executable: bool, mode: Option<u32>)` | composer `setDistributionFileMode` | Sets `mode & 0o777`, or 0755 or 0644 from `executable`. Does nothing on Windows. |
| `conventional_mode(executable: bool) -> u32` | composer `conventionalMode` | 0o755 or 0o644. |
| `create_dirs_0755(&Path)` | `os.MkdirAll(path, 0o755)` in filemetadata, jarpack and pluginpack | Creates the directory and its missing parents. Each new directory gets the mode 0755, also under the umask 002 or 077. A directory that exists keeps its mode. On Windows it only creates the directories. |
| `symlink(target: &Path, link: &Path, target_is_directory: bool)` | `os.Symlink` in the collector, composer, dev-launcher and pluginpack | Creates the link with the target text. On Unix it ignores the flag. On Windows it creates a directory link or a file link by the flag, and it replaces each `/` in the target with `\`. The error text is the Go `os.LinkError` text: `symlink <target> <link>: <cause>`. |
| `absolute_path(&Path) -> io::Result<PathBuf>` | `filepath.Abs` | Joins a relative path to the current directory and removes `.` and `..` lexically. |
| `real_path(&Path) -> io::Result<PathBuf>` | composer `realPath`, pluginpack `evalSymlinks` on Windows | `absolute_path`, then follows every link and junction. On Windows the result has no `\\?\` prefix (Go `stripExtendedPathPrefix`). |
| `resolve_links(&Path) -> io::Result<PathBuf>` | `filepath.EvalSymlinks`, pluginpack `evalSymlinks` on Unix | Follows every link. On Unix, a `..` after a link goes to the parent of the link target. The result is absolute. On Windows the result has no `\\?\` prefix. |
