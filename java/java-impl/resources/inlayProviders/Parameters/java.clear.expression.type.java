a.printSomething(switch (i) {
  case 1:
  yield "Apples";
  case 2:
  yield "Bananas";
default:
  yield "Apples and bananas";
  });

a.printSomething(Math.random() > 0.5 ? "Apples" : "Bananas");