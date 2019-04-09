class C {
    String method(Object o) {
        System.out.println(o);
        NewMethodResult x = newMethod(o);
        return x.returnResult;
    }

    NewMethodResult newMethod(Object o) {
        Integer i = new Integer(o.hashCode());
        return new NewMethodResult(i.toString());
    }

    static class NewMethodResult {
        private String returnResult;

        public NewMethodResult(String returnResult) {
            this.returnResult = returnResult;
        }
    }

    {
        Integer j = new Integer(Boolean.TRUE.hashCode());
        String k = j.toString();
    }
}