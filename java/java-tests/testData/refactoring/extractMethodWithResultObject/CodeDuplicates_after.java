class C {
    {
        int i = 0;

        NewMethodResult x = newMethod(i);
        System.out.println(128);
    }

    NewMethodResult newMethod(int i) {
        System.out.println(i);
        return new NewMethodResult();
    }

    static class NewMethodResult {
        public NewMethodResult() {
        }
    }
}