// "Extract Set from comparison chain" "true-preview"

class Orchard {
  boolean check(String fruit) {
    return "Apple"/*1*/./*2*/equals(/*3*/fruit)/* 4 */ ||
           "Pear".equals(fruit) ||/*5*/<caret>
           "Banana".equals(fruit)/*6*/;
  }
}