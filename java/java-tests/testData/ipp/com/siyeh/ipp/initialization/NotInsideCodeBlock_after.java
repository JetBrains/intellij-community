class X {
  Object obj<caret>;

    {
        obj = new Object() {
            String toString() {
                String message = "foo";
                return message;
            }
        };
    }
}