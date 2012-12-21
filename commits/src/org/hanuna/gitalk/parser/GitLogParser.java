package org.hanuna.gitalk.parser;

import org.hanuna.gitalk.commitmodel.Commit;
import org.hanuna.gitalk.commitmodel.Hash;
import org.hanuna.gitalk.commitmodel.builder.CommitListBuilder;
import org.hanuna.gitalk.commitmodel.builder.CommitLogData;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author erokhins
 */
public class GitLogParser {
    private static final String SEPARATOR = "\\|\\-";
    private static final String regExp =
            String.format("([a-f0-9]+)%1$s((?:[a-f0-9]+)?(?:\\s[a-f0-9]+)*)%1$s(.*?)%1$s([0-9]*)%1$s(.*)", SEPARATOR);
    private static final Pattern pattern = Pattern.compile(regExp);

    public static CommitLogData parseCommitData(String inputStr) {
        Matcher matcher = pattern.matcher(inputStr);
        if (matcher.matches()) {
            Hash hash = Hash.build(matcher.group(1));
            String parents = matcher.group(2);
            String author = matcher.group(3);
            long timeStamp = 0;
            if (matcher.group(4).length() != 0) {
                timeStamp = Long.parseLong(matcher.group(4));
            }
            String message = matcher.group(5);

            String[] parentsStr = parents.split("\\s");
            List<Hash> hashes = new ArrayList<Hash>(parentsStr.length);
            for (String aParentsStr : parentsStr) {
                if (aParentsStr.length() > 0) {
                    hashes.add(Hash.build(aParentsStr));
                }
            }
            return new CommitLogData(hash, Collections.unmodifiableList(hashes), message, author, timeStamp);
        } else {
            throw new IllegalArgumentException("unexpected format of string:" + inputStr);
        }
    }

    private final BufferedReader input;
    private final CommitListBuilder builder = new CommitListBuilder();

    public GitLogParser(Reader input) {
        if (input instanceof BufferedReader) {
            this.input = (BufferedReader) input;
        } else  {
            this.input = new BufferedReader(input);
        }
    }

    /**
     * Sometimes, we read not full git log and may be existed not read commit, that are refreshed read commit
     * If they existed - returned false, another - true.
     */
    public boolean allCommitsFound() {
        return builder.allCommitsFound();
    }

    // modifiable List
    public List<Commit> readAllCommits() throws IOException  {
        String line;
        while ((line = input.readLine()) != null) {
            CommitLogData logData = parseCommitData(line);
            builder.append(logData);
        }
        return builder.build();
    }

}
