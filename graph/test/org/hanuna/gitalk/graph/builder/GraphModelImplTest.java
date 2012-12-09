package org.hanuna.gitalk.graph.builder;

import org.hanuna.gitalk.commitmodel.Commit;
import org.hanuna.gitalk.common.ReadOnlyList;
import org.hanuna.gitalk.common.compressedlist.Replace;
import org.hanuna.gitalk.graph.Graph;
import org.hanuna.gitalk.graph.GraphFragment;
import org.hanuna.gitalk.graph.graph_elements.Branch;
import org.hanuna.gitalk.graph.mutable_graph.GraphBuilder;
import org.hanuna.gitalk.parser.GitLogParser;
import org.junit.Test;

import java.io.IOException;
import java.io.StringReader;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;
import static org.hanuna.gitalk.GraphTestUtils.getNode;
import static org.hanuna.gitalk.GraphTestUtils.toStr;

/**
 * @author erokhins
 */
public class GraphModelImplTest {

    private Graph builder(String inputTree) throws IOException {
        Branch.clearCountBranch();
        String input = inputTree.replace("\n", "|-aut|-132352112|-mes\n") + "|-aut|-132352112|-mes";
        GitLogParser parser = new GitLogParser(new StringReader(input));
        ReadOnlyList<Commit> commits = parser.readAllCommits();
        GraphBuilder builder = new GraphBuilder();
        return builder.build(commits);
    }

    @Test
    public void testSelect() throws IOException {
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
                "a0|-|-a0:a3:USUAL:0 a0:a1:USUAL:1 a0:a5:USUAL:2 a0:a12:USUAL:3|-COMMIT_NODE|-0|-0\n" +
                "a1|-a0:a1:USUAL:1|-a1:a2:USUAL:1|-COMMIT_NODE|-1|-1\n" +
                "a2|-a1:a2:USUAL:1|-a2:a4:USUAL:1|-COMMIT_NODE|-1|-2\n" +
                "a3|-a0:a3:USUAL:0|-a3:a5:USUAL:0 a3:a12:USUAL:4|-COMMIT_NODE|-0|-3\n" +
                "a5|-a0:a5:USUAL:2 a3:a5:USUAL:0|-a5:a5:USUAL:2|-EDGE_NODE|-2|-4\n" +
                "   a12|-a0:a12:USUAL:3 a3:a12:USUAL:4|-a12:a12:USUAL:3|-EDGE_NODE|-3|-4\n" +
                "   a4|-a2:a4:USUAL:1|-a4:a7:USUAL:1|-COMMIT_NODE|-1|-4\n" +
                "a5|-a5:a5:USUAL:2|-a5:a6:USUAL:2|-COMMIT_NODE|-2|-5\n" +
                "a6|-a5:a6:USUAL:2|-a6:a8:USUAL:2|-COMMIT_NODE|-2|-6\n" +
                "a7|-a4:a7:USUAL:1|-a7:a8:USUAL:1|-COMMIT_NODE|-1|-7\n" +
                "a8|-a6:a8:USUAL:2 a7:a8:USUAL:1|-|-COMMIT_NODE|-1|-8\n" +
                "a12|-a12:a12:USUAL:3|-|-END_COMMIT_NODE|-3|-9"
                ,
                toStr(graph)
        );

        GraphFragment piece = graph.relatePiece(getNode(graph, 1));
        assertTrue(piece != null);
        piece.setSelected(true);

        assertEquals("select",
                "a0|-|-a0:a3:USUAL:0 a0:a1:USUAL:1 a0:a5:USUAL:2 a0:a12:USUAL:3|-COMMIT_NODE|-0|-0\n" +
                "a1|-a0:a1:USUAL:1|-a1:a2:USUAL:1:s|-COMMIT_NODE|-1|-1|-s\n" +
                "a2|-a1:a2:USUAL:1:s|-a2:a4:USUAL:1:s|-COMMIT_NODE|-1|-2|-s\n" +
                "a3|-a0:a3:USUAL:0|-a3:a5:USUAL:0 a3:a12:USUAL:4|-COMMIT_NODE|-0|-3\n" +
                "a5|-a0:a5:USUAL:2 a3:a5:USUAL:0|-a5:a5:USUAL:2|-EDGE_NODE|-2|-4\n" +
                "   a12|-a0:a12:USUAL:3 a3:a12:USUAL:4|-a12:a12:USUAL:3|-EDGE_NODE|-3|-4\n" +
                "   a4|-a2:a4:USUAL:1:s|-a4:a7:USUAL:1:s|-COMMIT_NODE|-1|-4|-s\n" +
                "a5|-a5:a5:USUAL:2|-a5:a6:USUAL:2|-COMMIT_NODE|-2|-5\n" +
                "a6|-a5:a6:USUAL:2|-a6:a8:USUAL:2|-COMMIT_NODE|-2|-6\n" +
                "a7|-a4:a7:USUAL:1:s|-a7:a8:USUAL:1|-COMMIT_NODE|-1|-7|-s\n" +
                "a8|-a6:a8:USUAL:2 a7:a8:USUAL:1|-|-COMMIT_NODE|-1|-8\n" +
                "a12|-a12:a12:USUAL:3|-|-END_COMMIT_NODE|-3|-9"
                ,
                toStr(graph)
        );

        piece.setSelected(false);

        assertEquals("no select",
                "a0|-|-a0:a3:USUAL:0 a0:a1:USUAL:1 a0:a5:USUAL:2 a0:a12:USUAL:3|-COMMIT_NODE|-0|-0\n" +
                "a1|-a0:a1:USUAL:1|-a1:a2:USUAL:1|-COMMIT_NODE|-1|-1\n" +
                "a2|-a1:a2:USUAL:1|-a2:a4:USUAL:1|-COMMIT_NODE|-1|-2\n" +
                "a3|-a0:a3:USUAL:0|-a3:a5:USUAL:0 a3:a12:USUAL:4|-COMMIT_NODE|-0|-3\n" +
                "a5|-a0:a5:USUAL:2 a3:a5:USUAL:0|-a5:a5:USUAL:2|-EDGE_NODE|-2|-4\n" +
                "   a12|-a0:a12:USUAL:3 a3:a12:USUAL:4|-a12:a12:USUAL:3|-EDGE_NODE|-3|-4\n" +
                "   a4|-a2:a4:USUAL:1|-a4:a7:USUAL:1|-COMMIT_NODE|-1|-4\n" +
                "a5|-a5:a5:USUAL:2|-a5:a6:USUAL:2|-COMMIT_NODE|-2|-5\n" +
                "a6|-a5:a6:USUAL:2|-a6:a8:USUAL:2|-COMMIT_NODE|-2|-6\n" +
                "a7|-a4:a7:USUAL:1|-a7:a8:USUAL:1|-COMMIT_NODE|-1|-7\n" +
                "a8|-a6:a8:USUAL:2 a7:a8:USUAL:1|-|-COMMIT_NODE|-1|-8\n" +
                "a12|-a12:a12:USUAL:3|-|-END_COMMIT_NODE|-3|-9"
                ,
                toStr(graph)
        );


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
                "a0|-|-a0:a3:USUAL:0 a0:a1:USUAL:1 a0:a5:USUAL:2 a0:a12:USUAL:3|-COMMIT_NODE|-0|-0\n" +
                "a1|-a0:a1:USUAL:1|-a1:a2:USUAL:1|-COMMIT_NODE|-1|-1\n" +
                "a2|-a1:a2:USUAL:1|-a2:a4:USUAL:1|-COMMIT_NODE|-1|-2\n" +
                "a3|-a0:a3:USUAL:0|-a3:a5:USUAL:0 a3:a12:USUAL:4|-COMMIT_NODE|-0|-3\n" +
                "a5|-a0:a5:USUAL:2 a3:a5:USUAL:0|-a5:a5:USUAL:2|-EDGE_NODE|-2|-4\n" +
                "   a12|-a0:a12:USUAL:3 a3:a12:USUAL:4|-a12:a12:USUAL:3|-EDGE_NODE|-3|-4\n" +
                "   a4|-a2:a4:USUAL:1|-a4:a7:USUAL:1|-COMMIT_NODE|-1|-4\n" +
                "a5|-a5:a5:USUAL:2|-a5:a6:USUAL:2|-COMMIT_NODE|-2|-5\n" +
                "a6|-a5:a6:USUAL:2|-a6:a8:USUAL:2|-COMMIT_NODE|-2|-6\n" +
                "a7|-a4:a7:USUAL:1|-a7:a8:USUAL:1|-COMMIT_NODE|-1|-7\n" +
                "a8|-a6:a8:USUAL:2 a7:a8:USUAL:1|-|-COMMIT_NODE|-1|-8\n" +
                "a12|-a12:a12:USUAL:3|-|-END_COMMIT_NODE|-3|-9"
                ,
                toStr(graph)
        );

        GraphFragment fragment = graph.relatePiece(getNode(graph, 1));
        assertTrue(fragment != null);
        Replace replace = fragment.setVisible(false);
        assertEquals(1, replace.from());
        assertEquals(7, replace.to());
        assertEquals(4, replace.addElementsCount());

        assertEquals("hide",
                "a0|-|-a0:a3:USUAL:0 a0:a1:USUAL:1 a0:a5:USUAL:2 a0:a12:USUAL:3|-COMMIT_NODE|-0|-0\n" +
                "a1|-a0:a1:USUAL:1|-a1:a7:HIDE_PIECE:1|-COMMIT_NODE|-1|-1\n" +
                "a3|-a0:a3:USUAL:0|-a3:a5:USUAL:0 a3:a12:USUAL:4|-COMMIT_NODE|-0|-2\n" +
                "a5|-a0:a5:USUAL:2 a3:a5:USUAL:0|-a5:a5:USUAL:2|-EDGE_NODE|-2|-3\n" +
                "   a12|-a0:a12:USUAL:3 a3:a12:USUAL:4|-a12:a12:USUAL:3|-EDGE_NODE|-3|-3\n" +
                "a5|-a5:a5:USUAL:2|-a5:a6:USUAL:2|-COMMIT_NODE|-2|-4\n" +
                "a6|-a5:a6:USUAL:2|-a6:a8:USUAL:2|-COMMIT_NODE|-2|-5\n" +
                "a7|-a1:a7:HIDE_PIECE:1|-a7:a8:USUAL:1|-COMMIT_NODE|-1|-6\n" +
                "a8|-a6:a8:USUAL:2 a7:a8:USUAL:1|-|-COMMIT_NODE|-1|-7\n" +
                "a12|-a12:a12:USUAL:3|-|-END_COMMIT_NODE|-3|-8"
                ,
                toStr(graph)
        );


        replace = fragment.setVisible(true);
        assertEquals(1, replace.from());
        assertEquals(6, replace.to());
        assertEquals(5, replace.addElementsCount());

        assertEquals("show",
                "a0|-|-a0:a3:USUAL:0 a0:a1:USUAL:1 a0:a5:USUAL:2 a0:a12:USUAL:3|-COMMIT_NODE|-0|-0\n" +
                        "a1|-a0:a1:USUAL:1|-a1:a2:USUAL:1|-COMMIT_NODE|-1|-1\n" +
                        "a2|-a1:a2:USUAL:1|-a2:a4:USUAL:1|-COMMIT_NODE|-1|-2\n" +
                        "a3|-a0:a3:USUAL:0|-a3:a5:USUAL:0 a3:a12:USUAL:4|-COMMIT_NODE|-0|-3\n" +
                        "a5|-a0:a5:USUAL:2 a3:a5:USUAL:0|-a5:a5:USUAL:2|-EDGE_NODE|-2|-4\n" +
                        "   a12|-a0:a12:USUAL:3 a3:a12:USUAL:4|-a12:a12:USUAL:3|-EDGE_NODE|-3|-4\n" +
                        "   a4|-a2:a4:USUAL:1|-a4:a7:USUAL:1|-COMMIT_NODE|-1|-4\n" +
                        "a5|-a5:a5:USUAL:2|-a5:a6:USUAL:2|-COMMIT_NODE|-2|-5\n" +
                        "a6|-a5:a6:USUAL:2|-a6:a8:USUAL:2|-COMMIT_NODE|-2|-6\n" +
                        "a7|-a4:a7:USUAL:1|-a7:a8:USUAL:1|-COMMIT_NODE|-1|-7\n" +
                        "a8|-a6:a8:USUAL:2 a7:a8:USUAL:1|-|-COMMIT_NODE|-1|-8\n" +
                        "a12|-a12:a12:USUAL:3|-|-END_COMMIT_NODE|-3|-9"
                ,
                toStr(graph)
        );
    }

}
