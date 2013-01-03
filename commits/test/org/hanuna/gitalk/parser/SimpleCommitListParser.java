package org.hanuna.gitalk.parser;

import org.hanuna.gitalk.commitmodel.Commit;
import org.hanuna.gitalk.commitmodel.builder.CommitListBuilder;
import org.hanuna.gitalk.log.commit.CommitParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.List;

/**
 * @author erokhins
 */
public class SimpleCommitListParser {
    private final BufferedReader bufferedReader;
    private final CommitListBuilder builder = new CommitListBuilder();
    
    public SimpleCommitListParser(StringReader bufferedReader) {
        this.bufferedReader = new BufferedReader(bufferedReader);
    }

    public List<Commit> readAllCommits() throws IOException {
        String line;
        while ((line = bufferedReader.readLine()) != null) {
            builder.append(CommitParser.parseParentHashes(line));
        }
        return builder.build();
    }

    public boolean allCommitsFound() {
        return builder.allCommitsFound();
    }
}
