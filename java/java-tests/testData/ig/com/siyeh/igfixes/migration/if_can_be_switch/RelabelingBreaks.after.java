class RelabelingBreaks {
  int equalsOperator(String param, int i) {
      label:
      switch (i) {
        case 0:
            switch (param) {
                case "a":
                    if (i == 0) break label;
                    return 1;
                case "b":
                    return 2;
                default:
                    return 3;
            }
        default:
          return 1;
      }
      return -1;
  }
}