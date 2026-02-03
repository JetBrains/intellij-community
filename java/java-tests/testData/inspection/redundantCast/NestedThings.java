class XXX {
    String f() {
        new Runnable() {
            public void run() {
                new Runnable() {
                    public void run() {
                        new Runnable() {
                            public void run() {
                                new Runnable() {
                                    public void run() {
                                        new Runnable() {
                                            public void run() {
                                                String s = (<warning descr="Casting '\"\"' to 'String' is redundant">String</warning>)"";
                                            }
                                        };
                                    }
                                };
                            }
                        };
                    }
                };
            }
        };
        return "";
    }
    
    Runnable r = new Runnable() {
        public void run() {
            new Runnable() {
                public void run() {
                    new Runnable() {
                        public void run() {
                            new Runnable() {
                                public void run() {
                                    new Runnable() {
                                        public void run() {
                                            String s = (<warning descr="Casting '\"\"' to 'String' is redundant">String</warning>)"";
                                        }
                                    };
                                }
                            };
                        }
                    };
                }
            };
        }
    };
}
