package org.hanuna.gitalk.log.parser;

import org.hanuna.gitalk.log.commit.CommitParents;
import org.hanuna.gitalk.commit.Hash;
import org.hanuna.gitalk.log.commit.CommitData;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author erokhins
 */
public class CommitParser {

    /**
     * @param line input format:
     *             ab123|-adada 193 352
     *             123|-             // no parent
     */
    @NotNull
    public static CommitParents parseParentHashes(@NotNull String line) {
        int separatorIndex = line.indexOf("|-");
        if (separatorIndex == -1) {
            throw new IllegalArgumentException("not found separator \"|-\"");
        }
        String commitHashStr = line.substring(0, separatorIndex);
        Hash commitHash = Hash.build(commitHashStr);

        String parentHashStr = line.substring(separatorIndex + 2, line.length());
        String[] parentsHashes = parentHashStr.split("\\s");
        List<Hash> hashes = new ArrayList<Hash>(parentsHashes.length);
        for (String aParentsStr : parentsHashes) {
            if (aParentsStr.length() > 0) {
                hashes.add(Hash.build(aParentsStr));
            }
        }
        return new CommitParents(commitHash, hashes);
    }

    /**
     * @param line input format
     *             author name|-123124|-commit message
     */
    @NotNull
    public static CommitData parseCommitData(@NotNull String line) {
        int firstSeparatorIndex = line.indexOf("|-");
        if (firstSeparatorIndex == -1) {
            throw new IllegalArgumentException("not found first separator \"|-\" in this str: " + line);
        }
        String authorName = line.substring(0, firstSeparatorIndex);

        int secondSeparatorIndex = line.indexOf("|-", firstSeparatorIndex + 1);
        if (secondSeparatorIndex == -1) {
            throw new IllegalArgumentException("not found second separator \"|-\" in this str: " + line);
        }
        String timestampStr = line.substring(firstSeparatorIndex + 2, secondSeparatorIndex);
        long timestamp;
        try {
            if (timestampStr.isEmpty()) {
                timestamp = 0;
            } else {
                timestamp = Long.parseLong(timestampStr);
        }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("bad timestamp format: " + timestampStr + " in this Str: " + line);
        }

        String commitMessage = line.substring(secondSeparatorIndex + 2);

        return new CommitData(commitMessage, authorName, timestamp);
    }


}
