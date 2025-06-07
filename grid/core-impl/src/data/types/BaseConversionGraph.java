package com.intellij.database.data.types;

import com.intellij.database.datagrid.CoreGrid;
import com.intellij.database.datagrid.GridColumn;
import com.intellij.database.datagrid.GridRow;
import com.intellij.database.extractors.FormatterCreator;
import com.intellij.database.extractors.ObjectFormatter;
import com.intellij.database.run.ui.grid.editors.FormatsCache;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Ref;
import com.intellij.util.Function;
import com.intellij.util.Functions;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBTreeTraverser;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.containers.TreeTraversal;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Supplier;

public class BaseConversionGraph implements ConversionGraph {
  private static final Function<Object, Object> IDENTITY = o -> o;
  private static final Key<ConversionGraph> CONVERSION_GRAPH_KEY = new Key<>("CONVERSION_GRAPH_KEY");

  private final MultiMap<PointSet, Node> myMap;

  public BaseConversionGraph(@NotNull FormatsCache formatsCache,
                             @NotNull FormatterCreator formatterCreator,
                             @NotNull Supplier<ObjectFormatter> objectFormatter) {
    myMap = new MultiMap<>() {
      @Override
      protected @NotNull Collection<Node> createCollection() {
        return new LinkedHashSet<>();
      }
    };

    register(new DataConverter.TimestampToTemporal(formatsCache, formatterCreator));
    register(new DataConverter.TimeToTemporal());
    register(new DataConverter.BinaryTextToText());
    register(new DataConverter.BitStringToText());
    register(new DataConverter.BinaryToText());
    register(new DataConverter.BooleanToBinary());
    register(new DataConverter.BooleanToNumber());
    register(new DataConverter.BooleanToText());
    register(new DataConverter.BooleanNumberToText());
    register(new DataConverter.DateToNumber());
    register(new DataConverter.DateToText(formatsCache, formatterCreator));
    register(new DataConverter.DateToTimestamp());
    register(new DataConverter.NumberToText());
    register(new DataConverter.NumberToTimestamp());
    register(new DataConverter.TimestampToText(formatterCreator));
    register(new DataConverter.TimeToNumber());
    register(new DataConverter.TimeToText(formatterCreator));
    register(new DataConverter.TemporalTimeToTemporalTimestamp());
    register(new DataConverter.StringUuidToText());
    register(new DataConverter.UuidToText());
    register(new DataConverter.MapToText(objectFormatter));
    register(new DataConverter.ObjectToText(objectFormatter));
    register(new DataConverter.MoneyToText());
  }

  private @Nullable List<Node> shortestPath(@NotNull PointSet from, @NotNull PointSet to) {
    JBTreeTraverser<Node> traverser = JBTreeTraverser.from(node -> myMap.get(node.pointSet));
    TreeTraversal.TracingIt<Node> iterator = traverser.withRoots(myMap.get(from))
      .tracingBfsTraversal()
      .unique(node -> node.pointSet)
      .typedIterator();

    Ref<PointSet> parent = Ref.create(from);
    Map<PointSet, PointSet> path = new HashMap<>();
    iterator.forEachRemaining(node -> {
      Node itParent = iterator.parent();
      if (itParent != null && itParent.pointSet != parent.get()) parent.set(itParent.pointSet);
      path.put(node.pointSet, parent.get());
    });
    return path.containsKey(to) && path.containsKey(from) ? flat(path, from, to) : null;
  }

  private @NotNull List<Node> flat(@NotNull Map<PointSet, PointSet> path,
                                   @NotNull PointSet start,
                                   @NotNull PointSet end) {
    List<Node> nodes = new ArrayList<>();
    while (path.get(end) != null) {
      PointSet previous = path.get(end);
      final PointSet current = end;
      Node node = ContainerUtil.find(myMap.get(previous), currentNode -> Comparing.equal(currentNode.pointSet, current));
      if (node == null) return ContainerUtil.emptyList();
      nodes.add(node);
      end = path.get(end);
      if (end == start) break;
    }
    return ContainerUtil.reverse(nodes);
  }

  @Override
  public @Nullable Function<Object, Object> getConverter(@NotNull ConversionPoint<?> startPoint, @NotNull ConversionPoint<?> endPoint) {
    PointSet start = PointSet.of(startPoint);
    PointSet end = PointSet.of(endPoint);
    List<Node> nodes = shortestPath(start, end);
    if (nodes == null) return null;
    List<Function<Object, Object>> functions = ContainerUtil.map(nodes, node -> node.function);
    Function<Object, Object> resultFunction = null;
    for (Function<Object, Object> function : functions) {
      if (resultFunction == null) {
        resultFunction = function;
        continue;
      }
      resultFunction = Functions.compose(resultFunction, function);
    }
    return resultFunction;
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  public void register(@NotNull DataConverter converter) {
    PointSet start = converter.getStart();
    PointSet end = converter.getEnd();
    myMap.putValue(start, new Node(end, converter::convert));
    myMap.putValue(end, new Node(start, converter::convertReverse));
    myMap.putValue(start, new Node(start, IDENTITY));
    myMap.putValue(end, new Node(end, IDENTITY));
  }

  public static @NotNull ConversionGraph get(@NotNull CoreGrid<GridRow, GridColumn> grid) {
    return Objects.requireNonNull(grid.getUserData(CONVERSION_GRAPH_KEY));
  }

  public static void set(@NotNull CoreGrid<GridRow, GridColumn> grid, @NotNull ConversionGraph graph) {
    grid.putUserData(CONVERSION_GRAPH_KEY, graph);
  }

  private static final class Node {
    private final PointSet pointSet;
    private final Function<Object, Object> function;

    private Node(@NotNull PointSet pointSet, @Nullable Function<Object, Object> function) {
      this.pointSet = pointSet;
      this.function = function;
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof Node && pointSet.equals(((Node)obj).pointSet) && function == ((Node)obj).function;
    }

    @Override
    public int hashCode() {
      return pointSet.hashCode() + Objects.hashCode(function);
    }

    @Override
    public String toString() {
      return pointSet.toString();
    }
  }
}
