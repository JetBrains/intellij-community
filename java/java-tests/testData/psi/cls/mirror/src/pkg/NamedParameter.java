package pkg;

@NamedParameterAnno(type = 1, value = 2)
class NamedParameter  { }

@interface NamedParameterAnno {
    int value();
    int type();
}
