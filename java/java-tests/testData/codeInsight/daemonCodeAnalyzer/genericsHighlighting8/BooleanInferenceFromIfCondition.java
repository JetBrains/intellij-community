class Main {
    public <T> T getAttribute() {return null;}
    public <T> T getAttribute(T def) {return null;}

    {
        if (getAttribute()) {}

        while (getAttribute()) {}
        do {} while (getAttribute());
        for(int i = 0; getAttribute(); i++);
    }
}
