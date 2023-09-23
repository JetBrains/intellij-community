// "Replace with old style 'switch' statement" "true"

class ReturnCaseNullDefault {
  int bar(Object o) {
      switch (o) {
          case null:
          default:
              return 1;
      }
  }
}