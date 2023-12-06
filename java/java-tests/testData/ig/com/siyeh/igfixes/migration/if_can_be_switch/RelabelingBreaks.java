class RelabelingBreaks {
  int equalsOperator(String param, int i) {
    switch (i) {
      case 0:
        <caret>if (param == "a") {
          if (i == 0) break;
          return 1;
        } else if (param == "b") {
          return 2;
        } else {
          return 3;
        }
      default:
        return 1;
    }
    return -1;
  }
}