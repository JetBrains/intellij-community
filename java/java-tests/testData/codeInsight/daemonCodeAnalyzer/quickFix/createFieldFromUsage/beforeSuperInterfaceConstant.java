// "Create constant field 'bar' in 'I'" "false"
interface I {}

interface II extends I {}

class Usage {
  void usage() {
    II.bar<caret>;
  }
}