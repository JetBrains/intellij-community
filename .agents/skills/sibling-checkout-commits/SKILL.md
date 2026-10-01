---
name: sibling-checkout-commits
description: Take commits from a sibling checkout as patches, without a fetch.
---

# Commits from a sibling checkout

Use this skill when another checkout of the monorepo on this machine holds commits that this checkout needs,
for example a reorganization that a new change builds on. The user keeps the checkouts separate, so a clone, a
worktree or a shared directory is not an option.

## Why not fetch

A `git fetch /path/to/sibling` walks and packs objects over the whole history. With a hundred upstream commits of
difference it took more than 15 minutes here, and a second fetch of 12 new commits did not finish in 10 minutes.
It also leaves a remote-tracking ref and a copy of every object behind. A patch carries only the change.

## Procedure

1. Find the range in the sibling. The base is the last sibling commit this checkout already has; a commit taken
   before has a new hash here, so compare by subject or by patch id (`git patch-id --stable`), not by hash.
2. From the root of the receiving checkout run the script. It writes the patches under `out/sibling-patches/`,
   refuses when a patch touches a path that is dirty here, then applies and commits each patch in order:

   ```sh
   ./community/.agents/skills/sibling-checkout-commits/scripts/take-patches.sh ../idea-2 <base>..master
   ```

3. Read the `ok` lines. Each one names the new commit and confirms that its patch id equals the patch. A `DIFF` line
   stops the script; the commit on HEAD then holds something else, and the next patch is not applied.
4. On a weekend, the committer date is today: apply the weekend rule of the personal `CLAUDE.md` to the new commits.

## What the script does, and does not do

- `git apply --binary` changes the working tree only. `git commit --only -- <paths>` builds the commit from HEAD
  plus those paths, so nothing that a concurrent session staged in the shared index lands in the commit.
- A path that a patch creates or renames is untracked after the apply, and `--only` skips an untracked path. The
  script marks every such path with `git add -N` first; an intent-to-add entry carries no content.
- A path that a patch deletes stays in the pathspec, so the commit records the deletion.
- The author, the date and the message come from the patch through `git mailinfo`.
- It does not use `git am`: `git am` commits the shared index, and a concurrent session's staged files would land in
  the commit.
- It does not fetch, and it creates no ref. Delete a ref of an earlier fetch with `git update-ref -d refs/remotes/<name>/master`.

## When a patch does not apply

A patch of the range depends on the patches before it, so a `--check` of a later patch alone fails. The script
applies in order. When a patch fails against this tree, the sibling and this checkout diverged on that path:
stop, name the path and the commit in the report, and let the user decide. Do not resolve the conflict by hand
inside a commit that carries the sibling author's name.
