import module my.source.moduleB;
import module my.source.moduleB1;

class OptimizeImportWithSimilarNames {
  Imported1 module1;
  Imported2 <caret>module2;
}
