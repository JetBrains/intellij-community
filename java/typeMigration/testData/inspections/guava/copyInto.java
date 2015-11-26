import com.google.common.collect.FluentIterable;

import java.util.ArrayList;
import java.util.List;

class CopyInto {
  void m(FluentIt<caret>erable<String> it) {
    ArrayList<String> collection = new ArrayList<>();
    List<String> collection2 = it.copyInto(collection);
  }
}