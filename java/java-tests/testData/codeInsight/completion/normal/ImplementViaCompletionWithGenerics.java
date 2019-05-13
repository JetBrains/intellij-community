import java.util.List;

class Foo implements Bar {
  methW<caret>


}

interface Bar {
  <K> void methodWithTypeParam(K k);
  void methodWithGenerics(List<String> k);
}

