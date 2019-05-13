import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class Box<TBox>
{

  public TBox getValue()
  {
    return null;
  }

  void foo(Stream<Box<String>> stream){
    List<String> l1 = stream.map(Box<String>::getValue).collect(Collectors.toList());
    List<String> l2 = stream.map(Box::getValue).collect(Collectors.toList());
  }
}

