# fscopy

The copy and path helpers of the dev-distribution tools. All functions return `io::Result`. An error message names the
paths, and the `io::ErrorKind` of the cause stays the same.

Every copy goes through `clone_or_copy`, which uses `std::fs::copy`. On macOS it clones the file with `fclonefileat` on
APFS. On Linux it uses `copy_file_range`, which clones on Btrfs and XFS. On Windows it uses `CopyFileExW`, which
clones on ReFS and a Dev Drive.

## Differences from the former tool

- On macOS, a copy also gets the extended attributes and the ACL of the source, because the clone and `fcopyfile`
  copy them.
- On macOS, `real_path` and `resolve_links` return each name in the case that the file system stores. The former tool
  kept the case of the input.
- On Windows, `resolve_links` removes a `..` lexically before it follows a link. The former tool went to the parent of
  the link target.
- `copy_with_attributes` sets the modification time through `File::set_modified` on a handle without write access,
  because the copy can be read-only. On Windows the handle has only the right `FILE_WRITE_ATTRIBUTES`.
- On Windows, `symlink` takes the link kind from `target_is_directory`. The former tool read the kind from the target
  on the disk, so a missing target gave a file link.

## Public items

| Item | Former counterpart | Description |
|---|---|---|
| `clone_or_copy(&Path, &Path)` | `project-model-tree` `copyFile` | Fails with `AlreadyExists` when the destination exists, also for a dangling link. Follows a link at the source. Keeps the permission bits of the source. |
| `copy_with_attributes(&Path, &Path)` | composer `copyWithAttributes` | Java `Files.copy(COPY_ATTRIBUTES)` for a regular file: the clone keeps the permission bits, then the function sets the modification time. Refuses a directory, every other file type, and a mode with a setuid, setgid or sticky bit, with `InvalidInput`. The composer copies only regular files. |
| `copy_with_mode(&Path, &Path, executable: bool, mode: Option<u32>)` | collector `copyLocalFile` | Clones, then sets `mode & 0o777`, or 0755 or 0644 from `executable`. On Windows it sets only the read-only attribute from the owner write bit. |
| `replace_with_copy(&Path, &Path)` | pluginpack `copyFile` | Replaces an existing regular file (a reserved output) with a clone of the source. Fails with `NotFound` when the destination does not exist. The caller sets the mode after. |
| `set_distribution_file_mode(&Path, executable: bool, mode: Option<u32>)` | composer `setDistributionFileMode` | Sets `mode & 0o777`, or 0755 or 0644 from `executable`. Does nothing on Windows. |
| `conventional_mode(executable: bool) -> u32` | composer `conventionalMode` | 0o755 or 0o644. |
| `set_mode(&Path, mode: u32)` | `os.Chmod` in pluginpack | Sets the permission bits. On Windows it sets only the read-only attribute from the owner write bit, and it reads the attributes of the path itself, not of a link target. An error names the path. |
| `has_special_bits(&fs::Metadata) -> bool` | the special-mode check of the composer and pluginpack | Reports a setuid, setgid or sticky bit. On Windows it is always false, because NTFS stores no POSIX mode. |
| `symlink(target: &Path, link: &Path, target_is_directory: bool)` | `os.Symlink` in the collector, composer, dev-launcher and pluginpack | Creates the link with the target text. On Unix it ignores the flag. On Windows it creates a directory link or a file link by the flag, and it replaces each `/` in the target with `\`. The error text is `symlink <target> <link>: <cause>`. |
| `absolute_path(&Path) -> io::Result<PathBuf>` | `filepath.Abs` | Joins a relative path to the current directory and removes `.` and `..` lexically. |
| `real_path(&Path) -> io::Result<PathBuf>` | composer `realPath`, pluginpack `evalSymlinks` on Windows | `absolute_path`, then follows every link and junction. On Windows the result has no `\\?\` prefix. |
| `resolve_links(&Path) -> io::Result<PathBuf>` | `filepath.EvalSymlinks`, pluginpack `evalSymlinks` on Unix | Follows every link. On Unix, a `..` after a link goes to the parent of the link target. The result is absolute. On Windows the result has no `\\?\` prefix. |
