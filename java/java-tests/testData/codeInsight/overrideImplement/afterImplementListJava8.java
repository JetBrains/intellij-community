import java.util.Collections;
import java.util.List;

interface Whelmed {
  List<String> create();
}
class Overwhelmed implements Whelmed {
    @Override
    public List<String> create() {
        return Collections.emptyList();
    }
}