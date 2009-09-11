package com.intellij.dependencies;

import com.intellij.cyclicDependencies.CyclicGraphUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.graph.Graph;
import com.intellij.util.graph.GraphGenerator;
import junit.framework.TestCase;

import java.util.*;

/**
 * User: anna
 * Date: Feb 13, 2005
 */
public class SearchCyclesTest extends TestCase{
  public void test1() throws Exception{
    final HashMap<String, String []> graph = new HashMap<String, String[]>();
    graph.put("a", new String[]{"c", "d"});
    graph.put("b", new String[]{"a"});
    graph.put("c", new String[]{"d"});
    graph.put("d", new String[]{"a", "b"});
    Graph<String> graphToInvestigate = new GraphGenerator<String>(new GraphGenerator.SemiGraph<String>() {
      public Collection<String> getNodes() {
        return graph.keySet();
      }

      public Iterator<String> getIn(final String n) {
        String[] in = graph.get(n);
        if (in == null){
          in = ArrayUtil.EMPTY_STRING_ARRAY;
        }
        return Arrays.asList(in).iterator();
      }
    });
    final Set<List<String>> nodeCycles = CyclicGraphUtil.getNodeCycles(graphToInvestigate, "a");
    checkResult(new String[][]{{"d","a"},{"b","d","c","a"}}, nodeCycles);
  }

  public void test2() throws Exception{
    final HashMap<String, String []> graph = new HashMap<String, String[]>();
    graph.put("a", new String[]{"c"});
    graph.put("b", new String[]{"a"});
    graph.put("c", new String[]{"d"});
    graph.put("d", new String[]{"b","e"});
    graph.put("e", new String[]{"d"});
    Graph<String> graphToInvestigate = new GraphGenerator<String>(new GraphGenerator.SemiGraph<String>() {
      public Collection<String> getNodes() {
        return graph.keySet();
      }

      public Iterator<String> getIn(final String n) {
        String[] in = graph.get(n);
        if (in == null){
          in = ArrayUtil.EMPTY_STRING_ARRAY;
        }
        return Arrays.asList(in).iterator();
      }
    });
    final Set<List<String>> nodeCycles = CyclicGraphUtil.getNodeCycles(graphToInvestigate, "a");
    checkResult(new String[][]{{"b","d","c","a"}}, nodeCycles);
  }

  public void test3() throws Exception{
    final HashMap<String, String []> graph = new HashMap<String, String[]>();
    graph.put("a", new String[]{"d"});
    graph.put("b", new String[]{"a"});
    graph.put("d", new String[]{"a", "b"});
    Graph<String> graphToInvestigate = new GraphGenerator<String>(new GraphGenerator.SemiGraph<String>() {
      public Collection<String> getNodes() {
        return graph.keySet();
      }

      public Iterator<String> getIn(final String n) {
        String[] in = graph.get(n);
        if (in == null){
          in = ArrayUtil.EMPTY_STRING_ARRAY;
        }
        return Arrays.asList(in).iterator();
      }
    });
    final Set<List<String>> nodeCycles = CyclicGraphUtil.getNodeCycles(graphToInvestigate, "a");
    checkResult(new String[][]{{"d","a"}}, nodeCycles);
  }

   public void test4() throws Exception{
    final HashMap<String, String []> graph = new HashMap<String, String[]>();
    graph.put("a", new String[]{"c"});
    graph.put("b", new String[]{"a"});
    graph.put("c", new String[]{"e"});
    graph.put("d", new String[]{"b"});
    graph.put("e", new String[]{"d", "f"});
    graph.put("f", new String[]{"d"});
    Graph<String> graphToInvestigate = new GraphGenerator<String>(new GraphGenerator.SemiGraph<String>() {
      public Collection<String> getNodes() {
        return graph.keySet();
      }

      public Iterator<String> getIn(final String n) {
        String[] in = graph.get(n);
        if (in == null){
          in = ArrayUtil.EMPTY_STRING_ARRAY;
        }
        return Arrays.asList(in).iterator();
      }
    });
    final Set<List<String>> nodeCycles = CyclicGraphUtil.getNodeCycles(graphToInvestigate, "a");
    checkResult(new String[][]{{"b","d","e","c","a"}}, nodeCycles);
  }

   public void test5() throws Exception{
    final HashMap<String, String []> graph = new HashMap<String, String[]>();
    graph.put("a", new String[]{"c"});
    graph.put("b", new String[]{"a"});
    graph.put("c", new String[]{"e"});
    graph.put("d", new String[]{"b"});
    graph.put("e", new String[]{"d", "f", "a"});
    graph.put("f", new String[]{"d"});
    Graph<String> graphToInvestigate = new GraphGenerator<String>(new GraphGenerator.SemiGraph<String>() {
      public Collection<String> getNodes() {
        return graph.keySet();
      }

      public Iterator<String> getIn(final String n) {
        String[] in = graph.get(n);
        if (in == null){
          in = ArrayUtil.EMPTY_STRING_ARRAY;
        }
        return Arrays.asList(in).iterator();
      }
    });
    final Set<List<String>> nodeCycles = CyclicGraphUtil.getNodeCycles(graphToInvestigate, "a");
    checkResult(new String[][]{{"b","d","e","c","a"}, {"e","c","a"}}, nodeCycles);
  }

  private static void checkResult(String[][] expected, Set<List<String>> cycles){
    assertEquals(expected.length, cycles.size());
    for (List<String> strings : cycles) {
      assertTrue(findInMatrix(expected, strings.toArray(new String[strings.size()])) > -1);
    }
  }

  private static int findInMatrix(String [][] matrix, String [] string){
    for (int i = 0; i < matrix.length; i++) {
      String[] strings = matrix[i];
      if (Arrays.equals(strings, string)){
        return i;
      }
    }
    return -1;
  }
}
