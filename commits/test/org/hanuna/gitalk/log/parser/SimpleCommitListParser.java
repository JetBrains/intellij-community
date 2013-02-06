package org.hanuna.gitalk.log.parser;

import org.hanuna.gitalk.log.commit.Commit;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

/**
 * @author erokhins
 */
public class SimpleCommitListParser {
    private final BufferedReader bufferedReader;

    public SimpleCommitListParser(StringReader bufferedReader) {
        this.bufferedReader = new BufferedReader(bufferedReader);
    }

    public List<Commit> readAllCommits() throws IOException {
        String line;
        List<Commit> commits = new ArrayList<Commit>();
        while ((line = bufferedReader.readLine()) != null) {
            commits.add(CommitParser.parseParentHashes(line));
        }
        return commits;
    }

}
