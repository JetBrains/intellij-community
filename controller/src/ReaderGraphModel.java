import org.hanuna.gitalk.commitmodel.Commit;
import org.hanuna.gitalk.common.Timer;
import org.hanuna.gitalk.common.ReadOnlyList;
import org.hanuna.gitalk.graph.Graph;
import org.hanuna.gitalk.graph.mutable_graph.GraphBuilder;
import org.hanuna.gitalk.parser.GitLogParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * @author erokhins
 */
public class ReaderGraphModel {
    public static Graph read() throws IOException {
        Process p = Runtime.getRuntime().exec("git log --all --date-order --format=%h|-%p|-%an|-%ct|-%s");
        BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));

        Timer t = new Timer("git run + commits model");
        GitLogParser parser = new GitLogParser(r);
        ReadOnlyList<Commit> commits = parser.readAllCommits();
        t.print();

        t = new Timer("build graph model");
        GraphBuilder builder = new GraphBuilder();
        Graph graph = builder.build(commits);
        t.print();
        return graph;
    }
}
