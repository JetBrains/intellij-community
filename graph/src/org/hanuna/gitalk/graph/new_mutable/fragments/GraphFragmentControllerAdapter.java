package org.hanuna.gitalk.graph.new_mutable.fragments;

import org.hanuna.gitalk.common.compressedlist.Replace;
import org.hanuna.gitalk.graph.GraphFragmentController;
import org.hanuna.gitalk.graph.elements.Edge;
import org.hanuna.gitalk.graph.elements.GraphElement;
import org.hanuna.gitalk.graph.elements.GraphFragment;
import org.hanuna.gitalk.graph.elements.Node;
import org.jetbrains.annotations.NotNull;

/**
 * @author erokhins
 */
public class GraphFragmentControllerAdapter implements GraphFragmentController {
    private final FragmentManager fragmentManager;

    public GraphFragmentControllerAdapter(FragmentManager fragmentManager) {
        this.fragmentManager = fragmentManager;
    }

    @Override
    public GraphFragment relateFragment(@NotNull GraphElement graphElement) {
        NewGraphFragment fragment = fragmentManager.relateFragment(graphElement);
        if (fragment == null) {
            return null;
        } else {
            return new GraphFragmentAdapter(fragment);
        }
    }

    @NotNull
    @Override
    public Replace setVisible(@NotNull GraphFragment fragment, boolean visible) {
        NewGraphFragment newFragment = ((GraphFragmentAdapter) fragment).getFragment();
        if (newFragment.isVisible() == visible) {
            return Replace.ID_REPLACE;
        }
        if (visible) {
            return fragmentManager.show(newFragment);
        } else {
            return fragmentManager.hide(newFragment);
        }
    }

    @Override
    public boolean isVisible(@NotNull GraphFragment fragment) {
        return ((GraphFragmentAdapter) fragment).getFragment().isVisible();
    }

    private static class GraphFragmentAdapter implements GraphFragment {
        private final NewGraphFragment fragment;

        private GraphFragmentAdapter(NewGraphFragment fragment) {
            this.fragment = fragment;
        }

        public NewGraphFragment getFragment() {
            return fragment;
        }

        @NotNull
        @Override
        public Node getUpNode() {
            return fragment.getUpNode();
        }

        @NotNull
        @Override
        public Node getDownNode() {
            return fragment.getDownNode();
        }

        @Override
        public void intermediateWalker(@NotNull GraphElementRunnable graphRunnable) {
            graphRunnable.nodeRun(fragment.getUpNode());
            for (Edge edge : fragment.getUpNode().getDownEdges()) {
                graphRunnable.edgeRun(edge);
            }
            for (Node node : fragment.getIntermediateNodes()) {
                graphRunnable.nodeRun(node);
                for (Edge edge : node.getDownEdges()) {
                    graphRunnable.edgeRun(edge);
                }
            }
        }
    }
}
