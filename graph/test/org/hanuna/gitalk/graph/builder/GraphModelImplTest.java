package org.hanuna.gitalk.graph.builder;

import org.hanuna.gitalk.commitmodel.Commit;
import org.hanuna.gitalk.common.compressedlist.Replace;
import org.hanuna.gitalk.graph.Graph;
import org.hanuna.gitalk.graph.GraphFragmentController;
import org.hanuna.gitalk.graph.elements.GraphFragment;
import org.hanuna.gitalk.graph.mutable.GraphBuilder;
import org.hanuna.gitalk.parser.SimpleCommitListParser;
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

    private Graph builder(String input) throws IOException {
        SimpleCommitListParser parser = new SimpleCommitListParser(new StringReader(input));
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
                "a0|-|-a0:a12:USUAL:a12 a0:a1:USUAL:a1 a0:a3:USUAL:a0 a0:a5:USUAL:a5|-COMMIT_NODE|-a0|-0\n" +
                "a1|-a0:a1:USUAL:a1|-a1:a2:USUAL:a1|-COMMIT_NODE|-a1|-1\n" +
                "a2|-a1:a2:USUAL:a1|-a2:a4:USUAL:a1|-COMMIT_NODE|-a1|-2\n" +
                "a3|-a0:a3:USUAL:a0|-a3:a12:USUAL:a12 a3:a5:USUAL:a0|-COMMIT_NODE|-a0|-3\n" +
                "a5|-a0:a5:USUAL:a5 a3:a5:USUAL:a0|-a5:a5:USUAL:a5|-EDGE_NODE|-a5|-4\n" +
                "   a12|-a0:a12:USUAL:a12 a3:a12:USUAL:a12|-a12:a12:USUAL:a12|-EDGE_NODE|-a12|-4\n" +
                "   a4|-a2:a4:USUAL:a1|-a4:a7:USUAL:a1|-COMMIT_NODE|-a1|-4\n" +
                "a5|-a5:a5:USUAL:a5|-a5:a6:USUAL:a5|-COMMIT_NODE|-a5|-5\n" +
                "a6|-a5:a6:USUAL:a5|-a6:a8:USUAL:a5|-COMMIT_NODE|-a5|-6\n" +
                "a7|-a4:a7:USUAL:a1|-a7:a8:USUAL:a1|-COMMIT_NODE|-a1|-7\n" +
                "a8|-a6:a8:USUAL:a5 a7:a8:USUAL:a1|-|-COMMIT_NODE|-a5|-8\n" +
                "a12|-a12:a12:USUAL:a12|-|-END_COMMIT_NODE|-a12|-9"
                ,
                toStr(graph)
        );

        GraphFragmentController graphController = graph.getFragmentController();
        GraphFragment fragment = graphController.relateFragment(getNode(graph, 1));
        assertTrue(fragment != null);
        Replace replace = graphController.setVisible(fragment, false);
        assertEquals(1, replace.from());
        assertEquals(7, replace.to());
        assertEquals(4, replace.addElementsCount());

        assertEquals("hide",
                "a0|-|-a0:a12:USUAL:a12 a0:a1:USUAL:a1 a0:a3:USUAL:a0 a0:a5:USUAL:a5|-COMMIT_NODE|-a0|-0\n" +
                "a1|-a0:a1:USUAL:a1|-a1:a7:HIDE_FRAGMENT:a1|-COMMIT_NODE|-a1|-1\n" +
                "a3|-a0:a3:USUAL:a0|-a3:a12:USUAL:a12 a3:a5:USUAL:a0|-COMMIT_NODE|-a0|-2\n" +
                "a5|-a0:a5:USUAL:a5 a3:a5:USUAL:a0|-a5:a5:USUAL:a5|-EDGE_NODE|-a5|-3\n" +
                "   a12|-a0:a12:USUAL:a12 a3:a12:USUAL:a12|-a12:a12:USUAL:a12|-EDGE_NODE|-a12|-3\n" +
                "a5|-a5:a5:USUAL:a5|-a5:a6:USUAL:a5|-COMMIT_NODE|-a5|-4\n" +
                "a6|-a5:a6:USUAL:a5|-a6:a8:USUAL:a5|-COMMIT_NODE|-a5|-5\n" +
                "a7|-a1:a7:HIDE_FRAGMENT:a1|-a7:a8:USUAL:a1|-COMMIT_NODE|-a1|-6\n" +
                "a8|-a6:a8:USUAL:a5 a7:a8:USUAL:a1|-|-COMMIT_NODE|-a5|-7\n" +
                "a12|-a12:a12:USUAL:a12|-|-END_COMMIT_NODE|-a12|-8"
                ,
                toStr(graph)
        );


        replace = graphController.setVisible(fragment, true);
        assertEquals(1, replace.from());
        assertEquals(6, replace.to());
        assertEquals(5, replace.addElementsCount());

        assertEquals("show",
                "a0|-|-a0:a12:USUAL:a12 a0:a1:USUAL:a1 a0:a3:USUAL:a0 a0:a5:USUAL:a5|-COMMIT_NODE|-a0|-0\n" +
                "a1|-a0:a1:USUAL:a1|-a1:a2:USUAL:a1|-COMMIT_NODE|-a1|-1\n" +
                "a2|-a1:a2:USUAL:a1|-a2:a4:USUAL:a1|-COMMIT_NODE|-a1|-2\n" +
                "a3|-a0:a3:USUAL:a0|-a3:a12:USUAL:a12 a3:a5:USUAL:a0|-COMMIT_NODE|-a0|-3\n" +
                "a5|-a0:a5:USUAL:a5 a3:a5:USUAL:a0|-a5:a5:USUAL:a5|-EDGE_NODE|-a5|-4\n" +
                "   a12|-a0:a12:USUAL:a12 a3:a12:USUAL:a12|-a12:a12:USUAL:a12|-EDGE_NODE|-a12|-4\n" +
                "   a4|-a2:a4:USUAL:a1|-a4:a7:USUAL:a1|-COMMIT_NODE|-a1|-4\n" +
                "a5|-a5:a5:USUAL:a5|-a5:a6:USUAL:a5|-COMMIT_NODE|-a5|-5\n" +
                "a6|-a5:a6:USUAL:a5|-a6:a8:USUAL:a5|-COMMIT_NODE|-a5|-6\n" +
                "a7|-a4:a7:USUAL:a1|-a7:a8:USUAL:a1|-COMMIT_NODE|-a1|-7\n" +
                "a8|-a6:a8:USUAL:a5 a7:a8:USUAL:a1|-|-COMMIT_NODE|-a5|-8\n" +
                "a12|-a12:a12:USUAL:a12|-|-END_COMMIT_NODE|-a12|-9"
                ,
                toStr(graph)
        );
    }

}
