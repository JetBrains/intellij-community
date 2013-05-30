package org.hanuna.gitalk.printmodel.layout;

import org.hanuna.gitalk.common.compressedlist.CompressedList;
import org.hanuna.gitalk.common.compressedlist.RuntimeGenerateCompressedList;
import org.hanuna.gitalk.common.compressedlist.UpdateRequest;
import org.hanuna.gitalk.common.compressedlist.generator.Generator;
import org.hanuna.gitalk.graph.Graph;
import org.hanuna.gitalk.graph.elements.NodeRow;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author erokhins
 */
public class LayoutModel {
  private final Graph graph;
  private CompressedList<LayoutRow> layoutRowCompressedList;
  private final Generator<LayoutRow> generator;


  public LayoutModel(@NotNull Graph graph) {
    this.graph = graph;
    this.generator = new LayoutRowGenerator(graph);
    build();
  }

  private void build() {
    List<NodeRow> rows = graph.getNodeRows();
    layoutRowCompressedList = new RuntimeGenerateCompressedList<LayoutRow>(generator, rows.size(), 100);
  }


  @NotNull
  public List<LayoutRow> getLayoutRows() {
    return layoutRowCompressedList.getList();
  }

  public void recalculate(@NotNull UpdateRequest updateRequest) {
    layoutRowCompressedList.recalculate(updateRequest);
  }
}
