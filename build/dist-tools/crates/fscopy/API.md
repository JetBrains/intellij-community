# fscopy

The copy and path helpers of the distribution tools. All functions return `io::Result`. An error message names the
paths, and the `io::ErrorKind` of the cause stays the same.

Every copy goes through `clone_or_copy`, which uses `std::fs::copy`. On macOS it clones the file with `fclonefileat` on
APFS. On Linux it uses `copy_file_range`, which clones on Btrfs and XFS. On Windows it uses `CopyFileExW`, which
clones on ReFS and a Dev Drive.

## Platform behavior

- On macOS, a copy also gets the extended attributes and the ACL of the source, because the clone and `fcopyfile`
  copy them.
- On macOS, `real_path` and `resolve_links` return each name in the case that the file system stores.
- On Windows, `resolve_links` removes a `..` lexically before it follows a link.
- `copy_with_attributes` sets the modification time through `File::set_modified` on a handle without write access,
  because the copy can be read-only. On Windows the handle has only the right `FILE_WRITE_ATTRIBUTES`.
- On Windows, `symlink` takes the link kind from `target_is_directory` and does not read the target on the disk. So a
  link to a missing target gets the kind that the caller states.

## Public items

| Item | Description |
|---|---|
| `clone_or_copy(&Path, &Path)` | Fails with `AlreadyExists` when the destination exists, also for a dangling link. Follows a link at the source. Keeps the permission bits of the source. |
| `copy_with_attributes(&Path, &Path)` | Java `Files.copy(COPY_ATTRIBUTES)` for a regular file: the clone keeps the permission bits, then the function sets the modification time. Refuses a directory, every other file type, and a mode with a setuid, setgid or sticky bit, with `InvalidInput`. The composer copies only regular files. |
| `copy_with_mode(&Path, &Path, executable: bool, mode: Option<u32>)` | Clones, then sets `mode & 0o777`, or 0755 or 0644 from `executable`. On Windows it sets only the read-only attribute from the owner write bit. |
| `replace_with_copy(&Path, &Path)` | Replaces an existing regular file (a reserved output) with a clone of the source. Fails with `NotFound` when the destination does not exist. The caller sets the mode after. |
| `set_distribution_file_mode(&Path, executable: bool, mode: Option<u32>)` | Sets `mode & 0o777`, or 0755 or 0644 from `executable`. Does nothing on Windows. |
| `conventional_mode(executable: bool) -> u32` | 0o755 or 0o644. |
| `set_mode(&Path, mode: u32)` | Sets the permission bits. On Windows it sets only the read-only attribute from the owner write bit, and it reads the attributes of the path itself, not of a link target. An error names the path. |
| `has_special_bits(&fs::Metadata) -> bool` | Reports a setuid, setgid or sticky bit. On Windows it is always false, because NTFS stores no POSIX mode. |
| `symlink(target: &Path, link: &Path, target_is_directory: bool)` | Creates the link with the target text. On Unix it ignores the flag. On Windows it creates a directory link or a file link by the flag, and it replaces each `/` in the target with `\`. The error text is `symlink <target> <link>: <cause>`. |
| `absolute_path(&Path) -> io::Result<PathBuf>` | Joins a relative path to the current directory and removes `.` and `..` lexically. |
| `real_path(&Path) -> io::Result<PathBuf>` | `absolute_path`, then follows every link and junction. On Windows the result has no `\\?\` prefix. |
| `resolve_links(&Path) -> io::Result<PathBuf>` | Follows every link. On Unix, a `..` after a link goes to the parent of the link target. The result is absolute. On Windows the result has no `\\?\` prefix. |
