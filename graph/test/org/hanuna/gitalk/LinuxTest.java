package org.hanuna.gitalk;

import org.hanuna.gitalk.commitmodel.Commit;
import org.hanuna.gitalk.common.Timer;
import org.hanuna.gitalk.common.ReadOnlyList;
import org.hanuna.gitalk.graph.GraphModel;
import org.hanuna.gitalk.graph.builder.GraphModelBuilder;
import org.hanuna.gitalk.parser.GitLogParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * @author erokhins
 */
public class LinuxTest {

    public static void main(String[] args) throws IOException {
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

        //System.out.println(toStr(graph));
    }
}
