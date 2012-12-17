package org.hanuna.gitalk;

import org.hanuna.gitalk.commitmodel.Commit;
import org.hanuna.gitalk.common.ReadOnlyList;
import org.hanuna.gitalk.common.Timer;
import org.hanuna.gitalk.graph.Graph;
import org.hanuna.gitalk.graph.mutable_graph.GraphBuilder;
import org.hanuna.gitalk.parser.GitLogParser;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

/**
 * @author erokhins
 */
public class LinuxTest {

    public static void main(String[] args) throws IOException {
        //Process p = Runtime.getRuntime().exec("git log --all --date-order --format=%h|-%p|-%an|-%ct|-%s");

        //BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
        BufferedReader r = new BufferedReader(new FileReader("log"));

        Timer t = new Timer("git run + commits model");
        GitLogParser parser = new GitLogParser(r);
        ReadOnlyList<Commit> commits = parser.readAllCommits();
        t.print();

        while (true) {
            t = new Timer("build graph model");
            Graph graph = GraphBuilder.build(commits);
            t.print();
        }
        //System.out.println(toStr(graph));
    }
}
