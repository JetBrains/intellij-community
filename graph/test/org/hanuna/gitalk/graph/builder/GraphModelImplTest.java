package org.hanuna.gitalk.graph.builder;

import org.hanuna.gitalk.commitmodel.Commit;
import org.hanuna.gitalk.common.compressedlist.Replace;
import org.hanuna.gitalk.graph.Graph;
import org.hanuna.gitalk.graph.GraphFragment;
import org.hanuna.gitalk.graph.mutable_graph.GraphBuilder;
import org.hanuna.gitalk.graph.mutable_graph.MutableGraph;
import org.hanuna.gitalk.graph.mutable_graph.graph_fragment_controller.GraphFragmentController;
import org.hanuna.gitalk.graph.mutable_graph.graph_fragment_controller.SimpleGraphFragmentController;
import org.hanuna.gitalk.parser.GitLogParser;
import org.junit.Test;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;
import static org.hanuna.gitalk.GraphTestUtils.getNode;
import static org.hanuna.gitalk.GraphTestUtils.toStr;

/**
 * @author erokhins
 */
public class GraphModelImplTest {

    private Graph builder(String inputTree) throws IOException {
        String input = inputTree.replace("\n", "|-aut|-132352112|-mes\n") + "|-aut|-132352112|-mes";
        GitLogParser parser = new GitLogParser(new StringReader(input));
        List<Commit> commits = parser.readAllCommits();
        return GraphBuilder.build(commits);
    }


    @Test
    public void testHide() throws IOException {

        Graph graph = builder(
                "a0|-a3 a1 a5 a12\n" +
                "a1|-a2\n" +
                "a2|-a4\n" +
                "a3|-a5 a12\n" +
                "a4|-a7\n" +
                "a5|-a6\n" +
                "a6|-a8\n" +
                "a7|-a8\n" +
                "a8|-"
        );
        assertEquals("start",
                "a0|-|-a0:a3:USUAL:993 a0:a1:USUAL:994 a0:a5:USUAL:998 a0:a12:USUAL:31649|-COMMIT_NODE|-993|-0\n" +
                "a1|-a0:a1:USUAL:994|-a1:a2:USUAL:994|-COMMIT_NODE|-994|-1\n" +
                "a2|-a1:a2:USUAL:994|-a2:a4:USUAL:994|-COMMIT_NODE|-994|-2\n" +
                "a3|-a0:a3:USUAL:993|-a3:a5:USUAL:993 a3:a12:USUAL:31649|-COMMIT_NODE|-993|-3\n" +
                "a5|-a0:a5:USUAL:998 a3:a5:USUAL:993|-a5:a5:USUAL:998|-EDGE_NODE|-998|-4\n" +
                "   a12|-a0:a12:USUAL:31649 a3:a12:USUAL:31649|-a12:a12:USUAL:31649|-EDGE_NODE|-31649|-4\n" +
                "   a4|-a2:a4:USUAL:994|-a4:a7:USUAL:994|-COMMIT_NODE|-994|-4\n" +
                "a5|-a5:a5:USUAL:998|-a5:a6:USUAL:998|-COMMIT_NODE|-998|-5\n" +
                "a6|-a5:a6:USUAL:998|-a6:a8:USUAL:998|-COMMIT_NODE|-998|-6\n" +
                "a7|-a4:a7:USUAL:994|-a7:a8:USUAL:994|-COMMIT_NODE|-994|-7\n" +
                "a8|-a6:a8:USUAL:998 a7:a8:USUAL:994|-|-COMMIT_NODE|-998|-8\n" +
                "a12|-a12:a12:USUAL:31649|-|-END_COMMIT_NODE|-31649|-9"
                ,
                toStr(graph)
        );

        GraphFragmentController graphController = new SimpleGraphFragmentController((MutableGraph) graph);
        GraphFragment fragment = graphController.relateFragment(getNode(graph, 1));
        assertTrue(fragment != null);
        Replace replace = graphController.hideFragment(fragment);
        assertEquals(1, replace.from());
        assertEquals(7, replace.to());
        assertEquals(4, replace.addElementsCount());

        assertEquals("hide",
                "a0|-|-a0:a3:USUAL:993 a0:a1:USUAL:994 a0:a5:USUAL:998 a0:a12:USUAL:31649|-COMMIT_NODE|-993|-0\n" +
                "a1|-a0:a1:USUAL:994|-a1:a7:HIDE_FRAGMENT:994|-COMMIT_NODE|-994|-1\n" +
                "a3|-a0:a3:USUAL:993|-a3:a5:USUAL:993 a3:a12:USUAL:31649|-COMMIT_NODE|-993|-2\n" +
                "a5|-a0:a5:USUAL:998 a3:a5:USUAL:993|-a5:a5:USUAL:998|-EDGE_NODE|-998|-3\n" +
                "   a12|-a0:a12:USUAL:31649 a3:a12:USUAL:31649|-a12:a12:USUAL:31649|-EDGE_NODE|-31649|-3\n" +
                "a5|-a5:a5:USUAL:998|-a5:a6:USUAL:998|-COMMIT_NODE|-998|-4\n" +
                "a6|-a5:a6:USUAL:998|-a6:a8:USUAL:998|-COMMIT_NODE|-998|-5\n" +
                "a7|-a1:a7:HIDE_FRAGMENT:994|-a7:a8:USUAL:994|-COMMIT_NODE|-994|-6\n" +
                "a8|-a6:a8:USUAL:998 a7:a8:USUAL:994|-|-COMMIT_NODE|-998|-7\n" +
                "a12|-a12:a12:USUAL:31649|-|-END_COMMIT_NODE|-31649|-8"
                ,
                toStr(graph)
        );


        replace = graphController.showFragment(fragment);
        assertEquals(1, replace.from());
        assertEquals(6, replace.to());
        assertEquals(5, replace.addElementsCount());

        assertEquals("show",
                "a0|-|-a0:a3:USUAL:993 a0:a1:USUAL:994 a0:a5:USUAL:998 a0:a12:USUAL:31649|-COMMIT_NODE|-993|-0\n" +
                "a1|-a0:a1:USUAL:994|-a1:a2:USUAL:994|-COMMIT_NODE|-994|-1\n" +
                "a2|-a1:a2:USUAL:994|-a2:a4:USUAL:994|-COMMIT_NODE|-994|-2\n" +
                "a3|-a0:a3:USUAL:993|-a3:a5:USUAL:993 a3:a12:USUAL:31649|-COMMIT_NODE|-993|-3\n" +
                "a5|-a0:a5:USUAL:998 a3:a5:USUAL:993|-a5:a5:USUAL:998|-EDGE_NODE|-998|-4\n" +
                "   a12|-a0:a12:USUAL:31649 a3:a12:USUAL:31649|-a12:a12:USUAL:31649|-EDGE_NODE|-31649|-4\n" +
                "   a4|-a2:a4:USUAL:994|-a4:a7:USUAL:994|-COMMIT_NODE|-994|-4\n" +
                "a5|-a5:a5:USUAL:998|-a5:a6:USUAL:998|-COMMIT_NODE|-998|-5\n" +
                "a6|-a5:a6:USUAL:998|-a6:a8:USUAL:998|-COMMIT_NODE|-998|-6\n" +
                "a7|-a4:a7:USUAL:994|-a7:a8:USUAL:994|-COMMIT_NODE|-994|-7\n" +
                "a8|-a6:a8:USUAL:998 a7:a8:USUAL:994|-|-COMMIT_NODE|-998|-8\n" +
                "a12|-a12:a12:USUAL:31649|-|-END_COMMIT_NODE|-31649|-9"
                ,
                toStr(graph)
        );
    }

}
