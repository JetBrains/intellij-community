import java.util.List;

interface Whelmed {
  List<String> create();
}
class Overwhelmed implements Whelmed {
  <caret>
}