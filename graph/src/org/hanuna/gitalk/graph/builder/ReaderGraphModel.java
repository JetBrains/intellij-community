package org.hanuna.gitalk.graph.builder;

import org.hanuna.gitalk.commitmodel.Commit;
import org.hanuna.gitalk.common.Timer;
import org.hanuna.gitalk.common.ReadOnlyList;
import org.hanuna.gitalk.graph.GraphModel;
import org.hanuna.gitalk.parser.GitLogParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * @author erokhins
 */
public class ReaderGraphModel {
    public static GraphModel read() throws IOException {
        Process p = Runtime.getRuntime().exec("git log --all --date-order --format=%h|-%p|-%an|-%ct|-%s");
        BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));

        Timer t = new Timer("git run + commits model");
        GitLogParser parser = new GitLogParser(r);
        ReadOnlyList<Commit> commits = parser.getFullModel();
        t.print();

        t = new Timer("build graph model");
        GraphModelBuilder builder = new GraphModelBuilder();
        GraphModel graph = builder.build(commits);
        t.print();
        return graph;
    }
}
