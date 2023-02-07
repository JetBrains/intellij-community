// "Replace with text block" "false"
class Main {
  String foo(String s) {
    String x = "@<caret>InProceedings{6055279,\n" + s + "  Title                    = {Educational session 1},\n"
               + "  Booktitle                = {Custom Integrated Circuits Conference (CICC), 2011 IEEE},\n"
               + "  Year                     = {2011},\n" + "  Month                    = {Sept},\n";

  }
}