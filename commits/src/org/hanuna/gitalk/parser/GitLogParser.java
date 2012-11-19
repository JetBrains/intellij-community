package org.hanuna.gitalk.parser;

import org.hanuna.gitalk.commitmodel.Commit;
import org.hanuna.gitalk.commitmodel.Hash;
import org.hanuna.gitalk.commitmodel.builder.CommitListBuilder;
import org.hanuna.gitalk.commitmodel.builder.CommitLogData;
import org.hanuna.gitalk.common.readonly.ReadOnlyList;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
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
    private static final int DEFAULT_FIRST_PART_SIZE = 5000;

    public static CommitLogData parseCommitData(String inputStr) {
        Matcher matcher = pattern.matcher(inputStr);
        if (matcher.matches()) {
            Hash hash = Hash.buildHash(matcher.group(1));
            String parents = matcher.group(2);
            String author = matcher.group(3);
            long timeStamp = 0;
            if (matcher.group(4).length() != 0) {
                timeStamp = Long.parseLong(matcher.group(4));
            }
            String message = matcher.group(5);

            String[] parentsStr = parents.split("\\s");
            List<Hash> hashs = new ArrayList<Hash>(parentsStr.length);
            for (String aParentsStr : parentsStr) {
                if (aParentsStr.length() > 0) {
                    hashs.add(Hash.buildHash(aParentsStr));
                }
            }
            return new CommitLogData(hash, ReadOnlyList.newReadOnlyList(hashs), message, author, timeStamp);
        } else {
            throw new IllegalArgumentException("unexpected format of string:" + inputStr);
        }
    }

    private final BufferedReader input;
    private final CommitListBuilder builder = new CommitListBuilder();
    private final int firstPartSize;
    private int countLines = 0;


    public GitLogParser(Reader input) {
        this(input, DEFAULT_FIRST_PART_SIZE);
    }
    public GitLogParser(Reader input, int firstPartSize) {
        this.firstPartSize = firstPartSize;
        if (input instanceof BufferedReader) {
            this.input = (BufferedReader) input;
        } else  {
            this.input = new BufferedReader(input);
        }
    }

    public ReadOnlyList<Commit> getFirstPart() throws IOException {
        String line = null;
        while (countLines < firstPartSize && (line = input.readLine()) != null) {
            CommitLogData logData = parseCommitData(line);
            builder.append(logData);
            countLines++;
        }
        if (line == null) {
            return builder.build(true);
        } else {
            return builder.build(false);
        }
    }

    public ReadOnlyList<Commit> getFullModel() throws IOException  {
        String line = null;
        while ((line = input.readLine()) != null) {
            CommitLogData logData = parseCommitData(line);
            builder.append(logData);
            countLines++;
        }
        return builder.build(true);
    }

}
