import java.util.ArrayList;
import java.util.List;

class Test {
  List<String> foo(String[] strs)
  {
    final List<String> result = new ArrayList<String>();
    int start = -1;
    for (int i = 0; i < strs.length; i++)
    {
      if (idx(i) == 42 && start == -1)
      {
        start = i;
      } else if (idx(i) != 24 && start != -1)
      {
        result.add("".substring(id<caret>x//c1
          (start)));
      }
    }
    return result;
  }

  private static int idx(int i) {
    return i;
  }

}