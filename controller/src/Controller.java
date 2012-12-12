import org.hanuna.gitalk.commitmodel.Commit;
import org.hanuna.gitalk.common.ReadOnlyList;
import org.hanuna.gitalk.graph.Graph;
import org.hanuna.gitalk.graph.mutable_graph.GraphBuilder;
import org.hanuna.gitalk.refs.Ref;
import org.hanuna.gitalk.refs.RefsModel;

import java.io.IOException;

/**
 * @author erokhins
 */
public class Controller {
    private ReadOnlyList<Commit> commits;
    private ReadOnlyList<Ref> allRefs;
    private RefsModel refsModel;
    private Graph graph;

    public void prepare() throws IOException {
        commits = GitLogReader.readAllCommits();
        allRefs = GitLogReader.readAllRefs();
        refsModel = RefsModel.existedCommitRefs(allRefs, commits);
        graph = GraphBuilder.build(commits);
    }

    public RefsModel getRefsModel() {
        return refsModel;
    }

    public Graph getGraph() {
        return graph;
    }
}
