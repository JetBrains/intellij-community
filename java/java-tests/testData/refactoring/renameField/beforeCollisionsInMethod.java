class A {
    int <caret>fieldToBeRenamed;

    int method(int newFieldName) {
        if(newFieldName == 0) {
                return fieldToBeRenamed;
        }
        else {
            int newFieldName = fieldToBeRenamed;
            return fieldToBeRenamed + newFieldName;
        }
   }
}

