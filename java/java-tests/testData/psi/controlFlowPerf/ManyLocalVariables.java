public class ManyLocalVariables { // this

    public static void main(String[] args) {
        new Tester().testMe(1);
    }

    static class Tester {
        public boolean chk() {
            return true; //
        }

        public void testMe(int i) {

            Data1 d1 = (i < 0) ? null : new Data1(); //asdfasd
            Data2 d2 = (i < 0) ? null : new Data2();
            Data3 d3 = (i < 0) ? null : new Data3();
            Data4 d4 = (i < 0) ? null : new Data4();
            Data5 d5 = (i < 0) ? null : new Data5();
            Data6 d6 = (i < 0) ? null : new Data6();
            Data7 d7 = (i < 0) ? null : new Data7();
            int result = 1;
            int n = i - 2;
            int chk = 0;
            int v0 = r();
            int v1 = r();
            int v2 = r();
            int v3 = r();
            int v4 = r();
            int v5 = r();
            int v6 = r();
            int v7 = r();
            int v8 = r();
            int v9 = r();
            int va = r();
            int vb = r();
            int vc = r();
            int vd = r();
            int ve = r();
            int vf = r();

            v0++;
            n++;
            n++;
            n++;

            if( n > 0 ) {
                n++;
                n++;
                n++;
                n++;
            }

            if( d1.br(r()) ) {
                boolean b = d2.br(r())
                        && d3.br(r())
                        && d4.br(r())
                        && d5.br(r())
                        && this.chk();
                if( b ) {
                    evalMe:
                    if( n > 0 ) {
                        int x = d1.v() + d2.v() + d3.v() + d4.v() + d5.v() + d6.v() + d7.v() + (++n);
                        int y = d1.v() + d2.v() + d3.v() + d4.v() + d5.v() + d6.v() + d7.v() + (++n);
                        if( x > 0 ) {
                            if( y > 0 ) {
                                if( n > 0 ) {
                                    break evalMe;
                                }
                            }
                        }
                        result++;
                    }
                }
                v0 = 0;
            }

            if( n > 0 )
                n++;
            if( d1.br(r()) ) {
                boolean b = d2.br(r())
                        && d3.br(r())
                        && d4.br(r())
                        && d5.br(r());
                if( b ) {
                    evalMe:
                    if( n > 0 ) {
                        int x = d1.v() + d2.v() + d3.v() + d4.v() + d5.v() + d6.v() + d7.v() + (++n);
                        int y = d1.v() + d2.v() + d3.v() + d4.v() + d5.v() + d6.v() + d7.v() + (++n);
                        if( x > 0 ) {
                            if( y > 0 ) {
                                if( n > 0 ) {
                                    break evalMe;
                                }
                            }
                        }
                        result++;
                    }
                }
                v1 = v0 + 1;
            }

            if( n > 0 )
                n++;
            if( d1.br(r()) ) {
                boolean b = d2.br(r())
                        && d3.br(r())
                        && d4.br(r())
                        && d5.br(r());
                if( b ) {
                    evalMe:
                    if( n > 0 ) {
                        int x = d1.v() + d2.v() + d3.v() + d4.v() + d5.v() + d6.v() + d7.v() + (++n);
                        int y = d1.v() + d2.v() + d3.v() + d4.v() + d5.v() + d6.v() + d7.v() + (++n);
                        if( x > 0 ) {
                            if( y > 0 ) {
                                if( n > 0 ) {
                                    break evalMe;
                                }
                            }
                        }
                        result++;
                    }
                }
                v2 = v1 + 1;
            }

            if( n > 0 )
                n++;
            if( d1.br(r()) ) {
                boolean b = d2.br(r())
                        && d3.br(r())
                        && d4.br(r())
                        && d5.br(r());
                if( b ) {
                    evalMe:
                    if( n > 0 ) {
                        int x = d1.v() + d2.v() + d3.v() + d4.v() + d5.v() + d6.v() + d7.v() + (++n);
                        int y = d1.v() + d2.v() + d3.v() + d4.v() + d5.v() + d6.v() + d7.v() + (++n);
                        if( x > 0 ) {
                            if( y > 0 ) {
                                if( n > 0 ) {
                                    break evalMe;
                                }
                            }
                        }
                        result++;
                    }
                }
                v3 = v2 + 1;
            }

            if( n > 0 )
                n++;
            if( d1.br(r()) ) {
                boolean b = d2.br(r())
                        && d3.br(r())
                        && d4.br(r())
                        && d5.br(r());
                if( b ) {
                    evalMe:
                    if( n > 0 ) {
                        int x = d1.v() + d2.v() + d3.v() + d4.v() + d5.v() + d6.v() + d7.v() + (++n);
                        int y = d1.v() + d2.v() + d3.v() + d4.v() + d5.v() + d6.v() + d7.v() + (++n);
                        if( x > 0 ) {
                            if( y > 0 ) {
                                if( n > 0 ) {
                                    break evalMe;
                                }
                            }
                        }
                        result++;
                    }
                }
                v4 = v3 + 1;
            }

            if( n > 0 )
                n++;
            if( d1.br(r()) ) {
                boolean b = d2.br(r())
                        && d3.br(r())
                        && d4.br(r())
                        && d5.br(r());
                if( b ) {
                    evalMe:
                    if( n > 0 ) {
                        int x = d1.v() + d2.v() + d3.v() + d4.v() + d5.v() + d6.v() + d7.v() + (++n);
                        int y = d1.v() + d2.v() + d3.v() + d4.v() + d5.v() + d6.v() + d7.v() + (++n);
                        if( x > 0 ) {
                            if( y > 0 ) {
                                if( n > 0 ) {
                                    break evalMe;
                                }
                            }
                        }
                        result++;
                    }
                }
                v5 = v4 + 1;
            }

            if( n > 0 )
                n++;
            if( d1.br(r()) ) {
                boolean b = d2.br(r())
                        && d3.br(r())
                        && d4.br(r())
                        && d5.br(r());
                if( b ) {
                    evalMe:
                    if( n > 0 ) {
                        int x = d1.v() + d2.v() + d3.v() + d4.v() + d5.v() + d6.v() + d7.v() + (++n);
                        int y = d1.v() + d2.v() + d3.v() + d4.v() + d5.v() + d6.v() + d7.v() + (++n);
                        if( x > 0 ) {
                            if( y > 0 ) {
                                if( n > 0 ) {
                                    break evalMe;
                                }
                            }
                        }
                        result++;
                    }
                }
                v6 = v5 + 1;
            }

            if( n > 0 )
                n++;
            if( d1.br(r()) ) {
                boolean b = d2.br(r())
                        && d3.br(r())
                        && d4.br(r())
                        && d5.br(r());
                if( b ) {
                    evalMe:
                    if( n > 0 ) {
                        int x = d1.v() + d2.v() + d3.v() + d4.v() + d5.v() + d6.v() + d7.v() + (++n);
                        int y = d1.v() + d2.v() + d3.v() + d4.v() + d5.v() + d6.v() + d7.v() + (++n);
                        if( x > 0 ) {
                            if( y > 0 ) {
                                if( n > 0 ) {
                                    break evalMe;
                                }
                            }
                        }
                        result++;
                    }
                }
                v7 = v6 + 1;
            }

            if( n > 0 )
                n++;
            if( d1.br(r()) ) {
                boolean b = d2.br(r())
                        && d3.br(r())
                        && d4.br(r())
                        && d5.br(r());
                if( b ) {
                    evalMe:
                    if( n > 0 ) {
                        int x = d1.v() + d2.v() + d3.v() + d4.v() + d5.v() + d6.v() + d7.v() + (++n);
                        int y = d1.v() + d2.v() + d3.v() + d4.v() + d5.v() + d6.v() + d7.v() + (++n);
                        if( x > 0 ) {
                            if( y > 0 ) {
                                if( n > 0 ) {
                                    break evalMe;
                                }
                            }
                        }
                        result++;
                    }
                }
                v8 = v7 + 1;
            }

            if( n > 0 )
                n++;
            if( d1.br(r()) ) {
                boolean b = d2.br(r())
                        && d3.br(r())
                        && d4.br(r())
                        && d5.br(r());
                if( b ) {
                    evalMe:
                    if( n > 0 ) {
                        int x = d1.v() + d2.v() + d3.v() + d4.v() + d5.v() + d6.v() + d7.v() + (++n);
                        int y = d1.v() + d2.v() + d3.v() + d4.v() + d5.v() + d6.v() + d7.v() + (++n);
                        if( x > 0 ) {
                            if( y > 0 ) {
                                if( n > 0 ) {
                                    break evalMe;
                                }
                            }
                        }
                        result++;
                    }
                }
                v8 = v7 + 1;
            }

            if( n > 0 )
                n++;
            if( d1.br(r()) ) {
                boolean b = d2.br(r())
                        && d3.br(r())
                        && d4.br(r())
                        && d5.br(r());
                if( b ) {
                    evalMe:
                    if( n > 0 ) {
                        int x = d1.v() + d2.v() + d3.v() + d4.v() + d5.v() + d6.v() + d7.v() + (++n);
                        int y = d1.v() + d2.v() + d3.v() + d4.v() + d5.v() + d6.v() + d7.v() + (++n);
                        if( x > 0 ) {
                            if( y > 0 ) {
                                if( n > 0 ) {
                                    break evalMe;
                                }
                            }
                        }
                        result++;
                    }
                }
                v8 = v7 + 1;
            }

            if( n > 0 )
                n++;
            if( d1.br(r()) ) {
                boolean b = d2.br(r())
                        && d3.br(r())
                        && d4.br(r())
                        && d5.br(r());
                if( b ) {
                    evalMe:
                    if( n > 0 ) {
                        int x = d1.v() + d2.v() + d3.v() + d4.v() + d5.v() + d6.v() + d7.v() + (++n);
                        int y = d1.v() + d2.v() + d3.v() + d4.v() + d5.v() + d6.v() + d7.v() + (++n);
                        if( x > 0 ) {
                            if( y > 0 ) {
                                if( n > 0 ) {
                                    break evalMe;
                                }
                            }
                        }
                        result++;
                    }
                }
                v8 = v7 + 1;
            }

            if( n > 0 )
                n++;
            if( d1.br(r()) ) {
                boolean b = d2.br(r())
                        && d3.br(r())
                        && d4.br(r())
                        && d5.br(r());
                if( b ) {
                    evalMe:
                    if( n > 0 ) {
                        int x = d1.v() + d2.v() + d3.v() + d4.v() + d5.v() + d6.v() + d7.v() + (++n);
                        int y = d1.v() + d2.v() + d3.v() + d4.v() + d5.v() + d6.v() + d7.v() + (++n);
                        if( x > 0 ) {
                            if( y > 0 ) {
                                if( n > 0 ) {
                                    break evalMe;
                                }
                            }
                        }
                        result++;
                    }
                }
                v8 = v7 + 1;
            }

            if( n > 0 )
                n++;
            if( d1.br(r()) ) {
                boolean b = d2.br(r())
                        && d3.br(r())
                        && d4.br(r())
                        && d5.br(r());
                if( b ) {
                    evalMe:
                    if( n > 0 ) {
                        int x = d1.v() + d2.v() + d3.v() + d4.v() + d5.v() + d6.v() + d7.v() + (++n);
                        int y = d1.v() + d2.v() + d3.v() + d4.v() + d5.v() + d6.v() + d7.v() + (++n);
                        if( x > 0 ) {
                            if( y > 0 ) {
                                if( n > 0 ) {
                                    break evalMe;
                                }
                            }
                        }
                        result++;
                    }
                }
                v8 = v7 + 1;
            }

            if( n > 0 )
                n++;
            if( d1.br(r()) ) {
                boolean b = d2.br(r())
                        && d3.br(r())
                        && d4.br(r())
                        && d5.br(r());
                if( b ) {
                    evalMe:
                    if( n > 0 ) {
                        int x = d1.v() + d2.v() + d3.v() + d4.v() + d5.v() + d6.v() + d7.v() + (++n);
                        int y = d1.v() + d2.v() + d3.v() + d4.v() + d5.v() + d6.v() + d7.v() + (++n);
                        if( x > 0 ) {
                            if( y > 0 ) {
                                if( n > 0 ) {
                                    break evalMe;
                                }
                            }
                        }
                        result++;
                    }
                }
                v8 = v7 + 1;
            }

            if( n > 0 )
                n++;
            if( d1.br(r()) ) {
                boolean b = d2.br(r())
                        && d3.br(r())
                        && d4.br(r())
                        && d5.br(r());
                if( b ) {
                    evalMe:
                    if( n > 0 ) {
                        int x = d1.v() + d2.v() + d3.v() + d4.v() + d5.v() + d6.v() + d7.v() + (++n);
                        int y = d1.v() + d2.v() + d3.v() + d4.v() + d5.v() + d6.v() + d7.v() + (++n);
                        if( x > 0 ) {
                            if( y > 0 ) {
                                if( n > 0 ) {
                                    break evalMe;
                                }
                            }
                        }
                        result++;
                    }
                }
                v8 = v7 + 1;
            }

            if( n > 0 )
                n++;
            if( d1.br(r()) ) {
                boolean b = d2.br(r())
                        && d3.br(r())
                        && d4.br(r())
                        && d5.br(r());
                if( b ) {
                    evalMe:
                    if( n > 0 ) {
                        int x = d1.v() + d2.v() + d3.v() + d4.v() + d5.v() + d6.v() + d7.v() + (++n);
                        int y = d1.v() + d2.v() + d3.v() + d4.v() + d5.v() + d6.v() + d7.v() + (++n);
                        if( x > 0 ) {
                            if( y > 0 ) {
                                if( n > 0 ) {
                                    break evalMe;
                                }
                            }
                        }
                        result++;
                    }
                }
                v8 = v7 + 1;
            }

            if( n > 0 )
                n++;
            if( d1.br(r()) ) {
                boolean b = d2.br(r())
                        && d3.br(r())
                        && d4.br(r())
                        && d5.br(r());
                if( b ) {
                    evalMe:
                    if( n > 0 ) {
                        int x = d1.v() + d2.v() + d3.v() + d4.v() + d5.v() + d6.v() + d7.v() + (++n);
                        int y = d1.v() + d2.v() + d3.v() + d4.v() + d5.v() + d6.v() + d7.v() + (++n);
                        if( x > 0 ) {
                            if( y > 0 ) {
                                if( n > 0 ) {
                                    break evalMe;
                                }
                            }
                        }
                        result++;
                    }
                }
                v8 = v7 + 1;
            }

            if( n > 0 )
                n++;
            if( d1.br(r()) ) {
                boolean b = d2.br(r())
                        && d3.br(r())
                        && d4.br(r())
                        && d5.br(r());
                if( b ) {
                    evalMe:
                    if( n > 0 ) {
                        int x = d1.v() + d2.v() + d3.v() + d4.v() + d5.v() + d6.v() + d7.v() + (++n);
                        int y = d1.v() + d2.v() + d3.v() + d4.v() + d5.v() + d6.v() + d7.v() + (++n);
                        if( x > 0 ) {
                            if( y > 0 ) {
                                if( n > 0 ) {
                                    break evalMe;
                                }
                            }
                        }
                        result++;
                    }
                }
                v8 = v7 + 1;
            }

            if( n > 0 )
                n++;
            if( d1.br(r()) ) {
                boolean b = d2.br(r())
                        && d3.br(r())
                        && d4.br(r())
                        && d5.br(r());
                if( b ) {
                    evalMe:
                    if( n > 0 ) {
                        int x = d1.v() + d2.v() + d3.v() + d4.v() + d5.v() + d6.v() + d7.v() + (++n);
                        int y = d1.v() + d2.v() + d3.v() + d4.v() + d5.v() + d6.v() + d7.v() + (++n);
                        if( x > 0 ) {
                            if( y > 0 ) {
                                if( n > 0 ) {
                                    break evalMe;
                                }
                            }
                        }
                        result++;
                    }
                }
                v8 = v7 + 1;
            }

            if( n > 0 )
                n++;
            if( d1.br(r()) ) {
                boolean b = d2.br(r())
                        && d3.br(r())
                        && d4.br(r())
                        && d5.br(r());
                if( b ) {
                    evalMe:
                    if( n > 0 ) {
                        int x = d1.v() + d2.v() + d3.v() + d4.v() + d5.v() + d6.v() + d7.v() + (++n);
                        int y = d1.v() + d2.v() + d3.v() + d4.v() + d5.v() + d6.v() + d7.v() + (++n);
                        if( x > 0 ) {
                            if( y > 0 ) {
                                if( n > 0 ) {
                                    break evalMe;
                                }
                            }
                        }
                        result++;
                    }
                }
                v8 = v7 + 1;
            }

            if( n > 0 )
                n++;
            if( d1.br(r()) ) {
                boolean b = d2.br(r())
                        && d3.br(r())
                        && d4.br(r())
                        && d5.br(r());
                if( b ) {
                    evalMe:
                    if( n > 0 ) {
                        int x = d1.v() + d2.v() + d3.v() + d4.v() + d5.v() + d6.v() + d7.v() + (++n);
                        int y = d1.v() + d2.v() + d3.v() + d4.v() + d5.v() + d6.v() + d7.v() + (++n);
                        if( x > 0 ) {
                            if( y > 0 ) {
                                if( n > 0 ) {
                                    break evalMe;
                                }
                            }
                        }
                        result++;
                    }
                }
                v8 = v7 + 1;
            }

            if( n > 0 )
                n++;
            if( d1.br(r()) ) {
                boolean b = d2.br(r())
                        && d3.br(r())
                        && d4.br(r())
                        && d5.br(r());
                if( b ) {
                    evalMe:
                    if( n > 0 ) {
                        int x = d1.v() + d2.v() + d3.v() + d4.v() + d5.v() + d6.v() + d7.v() + (++n);
                        int y = d1.v() + d2.v() + d3.v() + d4.v() + d5.v() + d6.v() + d7.v() + (++n);
                        if( x > 0 ) {
                            if( y > 0 ) {
                                if( n > 0 ) {
                                    break evalMe;
                                }
                            }
                        }
                        result++;
                    }
                }
                v8 = v7 + 1;
            }

            if( n > 0 )
                n++;
            if( d1.br(r()) ) {
                boolean b = d2.br(r())
                        && d3.br(r())
                        && d4.br(r())
                        && d5.br(r());
                if( b ) {
                    evalMe:
                    if( n > 0 ) {
                        int x = d1.v() + d2.v() + d3.v() + d4.v() + d5.v() + d6.v() + d7.v() + (++n);
                        int y = d1.v() + d2.v() + d3.v() + d4.v() + d5.v() + d6.v() + d7.v() + (++n);
                        if( x > 0 ) {
                            if( y > 0 ) {
                                if( n > 0 ) {
                                    break evalMe;
                                }
                            }
                        }
                        result++;
                    }
                }
                v8 = v7 + 1;
            }

            if( n > 0 )
                n++;
            if( d1.br(r()) ) {
                boolean b = d2.br(r())
                        && d3.br(r())
                        && d4.br(r())
                        && d5.br(r());
                if( b ) {
                    evalMe:
                    if( n > 0 ) {
                        int x = d1.v() + d2.v() + d3.v() + d4.v() + d5.v() + d6.v() + d7.v() + (++n);
                        int y = d1.v() + d2.v() + d3.v() + d4.v() + d5.v() + d6.v() + d7.v() + (++n);
                        if( x > 0 ) {
                            if( y > 0 ) {
                                if( n > 0 ) {
                                    break evalMe;
                                }
                            }
                        }
                        result++;
                    }
                }
                v8 = v7 + 1;
            }

            if( n > 0 )
                n++;
            if( d1.br(r()) ) {
                boolean b = d2.br(r())
                        && d3.br(r())
                        && d4.br(r())
                        && d5.br(r());
                if( b ) {
                    evalMe:
                    if( n > 0 ) {
                        int x = d1.v() + d2.v() + d3.v() + d4.v() + d5.v() + d6.v() + d7.v() + (++n);
                        int y = d1.v() + d2.v() + d3.v() + d4.v() + d5.v() + d6.v() + d7.v() + (++n);
                        if( x > 0 ) {
                            if( y > 0 ) {
                                if( n > 0 ) {
                                    break evalMe;
                                }
                            }
                        }
                        result++;
                    }
                }
                v8 = v7 + 1;
            }

            if( n > 0 )
                n++;
            if( d1.br(r()) ) {
                boolean b = d2.br(r())
                        && d3.br(r())
                        && d4.br(r())
                        && d5.br(r());
                if( b ) {
                    evalMe:
                    if( n > 0 ) {
                        int x = d1.v() + d2.v() + d3.v() + d4.v() + d5.v() + d6.v() + d7.v() + (++n);
                        int y = d1.v() + d2.v() + d3.v() + d4.v() + d5.v() + d6.v() + d7.v() + (++n);
                        if( x > 0 ) {
                            if( y > 0 ) {
                                if( n > 0 ) {
                                    break evalMe;
                                }
                            }
                        }
                        result++;
                    }
                }
                v8 = v7 + 1;
            }

            if( n > 0 )
                n++;
            if( d1.br(r()) ) {
                boolean b = d2.br(r())
                        && d3.br(r())
                        && d4.br(r())
                        && d5.br(r());
                if( b ) {
                    evalMe:
                    if( n > 0 ) {
                        int x = d1.v() + d2.v() + d3.v() + d4.v() + d5.v() + d6.v() + d7.v() + (++n);
                        int y = d1.v() + d2.v() + d3.v() + d4.v() + d5.v() + d6.v() + d7.v() + (++n);
                        if( x > 0 ) {
                            if( y > 0 ) {
                                if( n > 0 ) {
                                    break evalMe;
                                }
                            }
                        }
                        result++;
                    }
                }
                v8 = v7 + 1;
            }

            if( n > 0 )
                n++;
            if( d1.br(r()) ) {
                boolean b = d2.br(r())
                        && d3.br(r())
                        && d4.br(r())
                        && d5.br(r());
                if( b ) {
                    evalMe:
                    if( n > 0 ) {
                        int x = d1.v() + d2.v() + d3.v() + d4.v() + d5.v() + d6.v() + d7.v() + (++n);
                        int y = d1.v() + d2.v() + d3.v() + d4.v() + d5.v() + d6.v() + d7.v() + (++n);
                        if( x > 0 ) {
                            if( y > 0 ) {
                                if( n > 0 ) {
                                    break evalMe;
                                }
                            }
                        }
                        result++;
                    }
                }
                v8 = v7 + 1;
            }

            if( n > 0 )
                n++;
            if( d1.br(r()) ) {
                boolean b = d2.br(r())
                        && d3.br(r())
                        && d4.br(r())
                        && d5.br(r());
                if( b ) {
                    evalMe:
                    if( n > 0 ) {
                        int x = d1.v() + d2.v() + d3.v() + d4.v() + d5.v() + d6.v() + d7.v() + (++n);
                        int y = d1.v() + d2.v() + d3.v() + d4.v() + d5.v() + d6.v() + d7.v() + (++n);
                        if( x > 0 ) {
                            if( y > 0 ) {
                                if( n > 0 ) {
                                    break evalMe;
                                }
                            }
                        }
                        result++;
                    }
                }
                v8 = v7 + 1;
            }

            if( n > 0 )
                n++;
            if( d1.br(r()) ) {
                boolean b = d2.br(r())
                        && d3.br(r())
                        && d4.br(r())
                        && d5.br(r());
                if( b ) {
                    evalMe:
                    if( n > 0 ) {
                        int x = d1.v() + d2.v() + d3.v() + d4.v() + d5.v() + d6.v() + d7.v() + (++n);
                        int y = d1.v() + d2.v() + d3.v() + d4.v() + d5.v() + d6.v() + d7.v() + (++n);
                        if( x > 0 ) {
                            if( y > 0 ) {
                                if( n > 0 ) {
                                    break evalMe;
                                }
                            }
                        }
                        result++;
                    }
                }
                v8 = v7 + 1;
            }

            if( n > 0 )
                n++;
            if( d1.br(r()) ) {
                boolean b = d2.br(r())
                        && d3.br(r())
                        && d4.br(r())
                        && d5.br(r());
                if( b ) {
                    evalMe:
                    if( n > 0 ) {
                        int x = d1.v() + d2.v() + d3.v() + d4.v() + d5.v() + d6.v() + d7.v() + (++n);
                        int y = d1.v() + d2.v() + d3.v() + d4.v() + d5.v() + d6.v() + d7.v() + (++n);
                        if( x > 0 ) {
                            if( y > 0 ) {
                                if( n > 0 ) {
                                    break evalMe;
                                }
                            }
                        }
                        result++;
                    }
                }
                v8 = v7 + 1;
            }

            if( n > 0 )
                n++;
            if( d1.br(r()) ) {
                boolean b = d2.br(r())
                        && d3.br(r())
                        && d4.br(r())
                        && d5.br(r());
                if( b ) {
                    evalMe:
                    if( n > 0 ) {
                        int x = d1.v() + d2.v() + d3.v() + d4.v() + d5.v() + d6.v() + d7.v() + (++n);
                        int y = d1.v() + d2.v() + d3.v() + d4.v() + d5.v() + d6.v() + d7.v() + (++n);
                        if( x > 0 ) {
                            if( y > 0 ) {
                                if( n > 0 ) {
                                    break evalMe;
                                }
                            }
                        }
                        result++;
                    }
                }
                v8 = v7 + 1;
            }

            if( n > 0 )
                n++;
            if( d1.br(r()) ) {
                boolean b = d2.br(r())
                        && d3.br(r())
                        && d4.br(r())
                        && d5.br(r());
                if( b ) {
                    evalMe:
                    if( n > 0 ) {
                        int x = d1.v() + d2.v() + d3.v() + d4.v() + d5.v() + d6.v() + d7.v() + (++n);
                        int y = d1.v() + d2.v() + d3.v() + d4.v() + d5.v() + d6.v() + d7.v() + (++n);
                        if( x > 0 ) {
                            if( y > 0 ) {
                                if( n > 0 ) {
                                    break evalMe;
                                }
                            }
                        }
                        result++;
                    }
                }
                v8 = v7 + 1;
            }

            if( n > 0 )
                n++;
            if( d1.br(r()) ) {
                boolean b = d2.br(r())
                        && d3.br(r())
                        && d4.br(r())
                        && d5.br(r());
                if( b ) {
                    evalMe:
                    if( n > 0 ) {
                        int x = d1.v() + d2.v() + d3.v() + d4.v() + d5.v() + d6.v() + d7.v() + (++n);
                        int y = d1.v() + d2.v() + d3.v() + d4.v() + d5.v() + d6.v() + d7.v() + (++n);
                        if( x > 0 ) {
                            if( y > 0 ) {
                                if( n > 0 ) {
                                    break evalMe;
                                }
                            }
                        }
                        result++;
                    }
                }
                v8 = v7 + 1;
            }

            if( n > 0 )
                n++;
            if( d1.br(r()) ) {
                boolean b = d2.br(r())
                        && d3.br(r())
                        && d4.br(r())
                        && d5.br(r());
                if( b ) {
                    evalMe:
                    if( n > 0 ) {
                        int x = d1.v() + d2.v() + d3.v() + d4.v() + d5.v() + d6.v() + d7.v() + (++n);
                        int y = d1.v() + d2.v() + d3.v() + d4.v() + d5.v() + d6.v() + d7.v() + (++n);
                        if( x > 0 ) {
                            if( y > 0 ) {
                                if( n > 0 ) {
                                    break evalMe;
                                }
                            }
                        }
                        result++;
                    }
                }
                v8 = v7 + 1;
            }

            if( n > 0 )
                n++;
            if( d1.br(r()) ) {
                boolean b = d2.br(r())
                        && d3.br(r())
                        && d4.br(r())
                        && d5.br(r());
                if( b ) {
                    evalMe:
                    if( n > 0 ) {
                        int x = d1.v() + d2.v() + d3.v() + d4.v() + d5.v() + d6.v() + d7.v() + (++n);
                        int y = d1.v() + d2.v() + d3.v() + d4.v() + d5.v() + d6.v() + d7.v() + (++n);
                        if( x > 0 ) {
                            if( y > 0 ) {
                                if( n > 0 ) {
                                    break evalMe;
                                }
                            }
                        }
                        result++;
                    }
                }
                v8 = v7 + 1;
            }

            if( n > 0 )
                n++;
            if( d1.br(r()) ) {
                boolean b = d2.br(r())
                        && d3.br(r())
                        && d4.br(r())
                        && d5.br(r());
                if( b ) {
                    evalMe:
                    if( n > 0 ) {
                        int x = d1.v() + d2.v() + d3.v() + d4.v() + d5.v() + d6.v() + d7.v() + (++n);
                        int y = d1.v() + d2.v() + d3.v() + d4.v() + d5.v() + d6.v() + d7.v() + (++n);
                        if( x > 0 ) {
                            if( y > 0 ) {
                                if( n > 0 ) {
                                    break evalMe;
                                }
                            }
                        }
                        result++;
                    }
                }
                v8 = v7 + 1;
            }

            if( n > 0 )
                n++;
            if( d1.br(r()) ) {
                boolean b = d2.br(r())
                        && d3.br(r())
                        && d4.br(r())
                        && d5.br(r());
                if( b ) {
                    evalMe:
                    if( n > 0 ) {
                        int x = d1.v() + d2.v() + d3.v() + d4.v() + d5.v() + d6.v() + d7.v() + (++n);
                        int y = d1.v() + d2.v() + d3.v() + d4.v() + d5.v() + d6.v() + d7.v() + (++n);
                        if( x > 0 ) {
                            if( y > 0 ) {
                                if( n > 0 ) {
                                    break evalMe;
                                }
                            }
                        }
                        result++;
                    }
                }
                v8 = v7 + 1;
            }

            if( n > 0 )
                n++;
            if( d1.br(r()) ) {
                boolean b = d2.br(r())
                        && d3.br(r())
                        && d4.br(r())
                        && d5.br(r());
                if( b ) {
                    evalMe:
                    if( n > 0 ) {
                        int x = d1.v() + d2.v() + d3.v() + d4.v() + d5.v() + d6.v() + d7.v() + (++n);
                        int y = d1.v() + d2.v() + d3.v() + d4.v() + d5.v() + d6.v() + d7.v() + (++n);
                        if( x > 0 ) {
                            if( y > 0 ) {
                                if( n > 0 ) {
                                    break evalMe;
                                }
                            }
                        }
                        result++;
                    }
                }
                v8 = v7 + 1;
            }

            if( n > 0 )
                n++;
            if( d1.br(r()) ) {
                boolean b = d2.br(r())
                        && d3.br(r())
                        && d4.br(r())
                        && d5.br(r());
                if( b ) {
                    evalMe:
                    if( n > 0 ) {
                        int x = d1.v() + d2.v() + d3.v() + d4.v() + d5.v() + d6.v() + d7.v() + (++n);
                        int y = d1.v() + d2.v() + d3.v() + d4.v() + d5.v() + d6.v() + d7.v() + (++n);
                        if( x > 0 ) {
                            if( y > 0 ) {
                                if( n > 0 ) {
                                    break evalMe;
                                }
                            }
                        }
                        result++;
                    }
                }
                v8 = v7 + 1;
            }

            if( n > 0 )
                n++;
            if( d1.br(r()) ) {
                boolean b = d2.br(r())
                        && d3.br(r())
                        && d4.br(r())
                        && d5.br(r());
                if( b ) {
                    evalMe:
                    if( n > 0 ) {
                        int x = d1.v() + d2.v() + d3.v() + d4.v() + d5.v() + d6.v() + d7.v() + (++n);
                        int y = d1.v() + d2.v() + d3.v() + d4.v() + d5.v() + d6.v() + d7.v() + (++n);
                        if( x > 0 ) {
                            if( y > 0 ) {
                                if( n > 0 ) {
                                    break evalMe;
                                }
                            }
                        }
                        result++;
                    }
                }
                v8 = v7 + 1;
            }

            if( n > 0 )
                n++;
            if( d1.br(r()) ) {
                boolean b = d2.br(r())
                        && d3.br(r())
                        && d4.br(r())
                        && d5.br(r());
                if( b ) {
                    evalMe:
                    if( n > 0 ) {
                        int x = d1.v() + d2.v() + d3.v() + d4.v() + d5.v() + d6.v() + d7.v() + (++n);
                        int y = d1.v() + d2.v() + d3.v() + d4.v() + d5.v() + d6.v() + d7.v() + (++n);
                        if( x > 0 ) {
                            if( y > 0 ) {
                                if( n > 0 ) {
                                    break evalMe;
                                }
                            }
                        }
                        result++;
                    }
                }
                v8 = v7 + 1;
            }

            if( n > 0 )
                n++;
            if( d1.br(r()) ) {
                boolean b = d2.br(r())
                        && d3.br(r())
                        && d4.br(r())
                        && d5.br(r());
                if( b ) {
                    evalMe:
                    if( n > 0 ) {
                        int x = d1.v() + d2.v() + d3.v() + d4.v() + d5.v() + d6.v() + d7.v() + (++n);
                        int y = d1.v() + d2.v() + d3.v() + d4.v() + d5.v() + d6.v() + d7.v() + (++n);
                        if( x > 0 ) {
                            if( y > 0 ) {
                                if( n > 0 ) {
                                    break evalMe;
                                }
                            }
                        }
                        result++;
                    }
                }
                v8 = v7 + 1;
            }

            if( n > 0 )
                n++;
            if( d1.br(r()) ) {
                boolean b = d2.br(r())
                        && d3.br(r())
                        && d4.br(r())
                        && d5.br(r());
                if( b ) {
                    evalMe:
                    if( n > 0 ) {
                        int x = d1.v() + d2.v() + d3.v() + d4.v() + d5.v() + d6.v() + d7.v() + (++n);
                        int y = d1.v() + d2.v() + d3.v() + d4.v() + d5.v() + d6.v() + d7.v() + (++n);
                        if( x > 0 ) {
                            if( y > 0 ) {
                                if( n > 0 ) {
                                    break evalMe;
                                }
                            }
                        }
                        result++;
                    }
                }
                v8 = v7 + 1;
            }

            if( n > 0 )
                n++;
            if( d1.br(r()) ) {
                boolean b = d2.br(r())
                        && d3.br(r())
                        && d4.br(r())
                        && d5.br(r());
                if( b ) {
                    evalMe:
                    if( n > 0 ) {
                        int x = d1.v() + d2.v() + d3.v() + d4.v() + d5.v() + d6.v() + d7.v() + (++n);
                        int y = d1.v() + d2.v() + d3.v() + d4.v() + d5.v() + d6.v() + d7.v() + (++n);
                        if( x > 0 ) {
                            if( y > 0 ) {
                                if( n > 0 ) {
                                    break evalMe;
                                }
                            }
                        }
                        result++;
                    }
                }
                v8 = v7 + 1;
            }

            if( n > 0 )
                n++;
            if( d1.br(r()) ) {
                boolean b = d2.br(r())
                        && d3.br(r())
                        && d4.br(r())
                        && d5.br(r());
                if( b ) {
                    evalMe:
                    if( n > 0 ) {
                        int x = d1.v() + d2.v() + d3.v() + d4.v() + d5.v() + d6.v() + d7.v() + (++n);
                        int y = d1.v() + d2.v() + d3.v() + d4.v() + d5.v() + d6.v() + d7.v() + (++n);
                        if( x > 0 ) {
                            if( y > 0 ) {
                                if( n > 0 ) {
                                    break evalMe;
                                }
                            }
                        }
                        result++;
                    }
                }
                v8 = v7 + 1;
            }

            if( n > 0 )
                n++;
            if( d1.br(r()) ) {
                boolean b = d2.br(r())
                        && d3.br(r())
                        && d4.br(r())
                        && d5.br(r());
                if( b ) {
                    evalMe:
                    if( n > 0 ) {
                        int x = d1.v() + d2.v() + d3.v() + d4.v() + d5.v() + d6.v() + d7.v() + (++n);
                        int y = d1.v() + d2.v() + d3.v() + d4.v() + d5.v() + d6.v() + d7.v() + (++n);
                        if( x > 0 ) {
                            if( y > 0 ) {
                                if( n > 0 ) {
                                    break evalMe;
                                }
                            }
                        }
                        result++;
                    }
                }
                v8 = v7 + 1;
            }

            if( n > 0 )
                n++;
            if( d1.br(r()) ) {
                boolean b = d2.br(r())
                        && d3.br(r())
                        && d4.br(r())
                        && d5.br(r());
                if( b ) {
                    evalMe:
                    if( n > 0 ) {
                        int x = d1.v() + d2.v() + d3.v() + d4.v() + d5.v() + d6.v() + d7.v() + (++n);
                        int y = d1.v() + d2.v() + d3.v() + d4.v() + d5.v() + d6.v() + d7.v() + (++n);
                        if( x > 0 ) {
                            if( y > 0 ) {
                                if( n > 0 ) {
                                    break evalMe;
                                }
                            }
                        }
                        result++;
                    }
                }
                v8 = v7 + 1;
            }

            if( n > 0 )
                n++;
            if( d1.br(r()) ) {
                boolean b = d2.br(r())
                        && d3.br(r())
                        && d4.br(r())
                        && d5.br(r());
                if( b ) {
                    evalMe:
                    if( n > 0 ) {
                        int x = d1.v() + d2.v() + d3.v() + d4.v() + d5.v() + d6.v() + d7.v() + (++n);
                        int y = d1.v() + d2.v() + d3.v() + d4.v() + d5.v() + d6.v() + d7.v() + (++n);
                        if( x > 0 ) {
                            if( y > 0 ) {
                                if( n > 0 ) {
                                    break evalMe;
                                }
                            }
                        }
                        result++;
                    }
                }
                v8 = v7 + 1;
            }

            if( n > 0 )
                n++;
            if( d1.br(r()) ) {
                boolean b = d2.br(r())
                        && d3.br(r())
                        && d4.br(r())
                        && d5.br(r());
                if( b ) {
                    evalMe:
                    if( n > 0 ) {
                        int x = d1.v() + d2.v() + d3.v() + d4.v() + d5.v() + d6.v() + d7.v() + (++n);
                        int y = d1.v() + d2.v() + d3.v() + d4.v() + d5.v() + d6.v() + d7.v() + (++n);
                        if( x > 0 ) {
                            if( y > 0 ) {
                                if( n > 0 ) {
                                    break evalMe;
                                }
                            }
                        }
                        result++;
                    }
                }
                v8 = v7 + 1;
            }

            if( n > 0 )
                n++;
            if( d1.br(r()) ) {
                boolean b = d2.br(r())
                        && d3.br(r())
                        && d4.br(r())
                        && d5.br(r());
                if( b ) {
                    evalMe:
                    if( n > 0 ) {
                        int x = d1.v() + d2.v() + d3.v() + d4.v() + d5.v() + d6.v() + d7.v() + (++n);
                        int y = d1.v() + d2.v() + d3.v() + d4.v() + d5.v() + d6.v() + d7.v() + (++n);
                        if( x > 0 ) {
                            if( y > 0 ) {
                                if( n > 0 ) {
                                    break evalMe;
                                }
                            }
                        }
                        result++;
                    }
                }
                v8 = v7 + 1;
            }

            if( n > 0 )
                n++;
            if( d1.br(r()) ) {
                boolean b = d2.br(r())
                        && d3.br(r())
                        && d4.br(r())
                        && d5.br(r());
                if( b ) {
                    evalMe:
                    if( n > 0 ) {
                        int x = d1.v() + d2.v() + d3.v() + d4.v() + d5.v() + d6.v() + d7.v() + (++n);
                        int y = d1.v() + d2.v() + d3.v() + d4.v() + d5.v() + d6.v() + d7.v() + (++n);
                        if( x > 0 ) {
                            if( y > 0 ) {
                                if( n > 0 ) {
                                    break evalMe;
                                }
                            }
                        }
                        result++;
                    }
                }
                v8 = v7 + 1;
            }

            if( n > 0 )
                n++;
            if( d1.br(r()) ) {
                boolean b = d2.br(r())
                        && d3.br(r())
                        && d4.br(r())
                        && d5.br(r());
                if( b ) {
                    evalMe:
                    if( n > 0 ) {
                        int x = d1.v() + d2.v() + d3.v() + d4.v() + d5.v() + d6.v() + d7.v() + (++n);
                        int y = d1.v() + d2.v() + d3.v() + d4.v() + d5.v() + d6.v() + d7.v() + (++n);
                        if( x > 0 ) {
                            if( y > 0 ) {
                                if( n > 0 ) {
                                    break evalMe;
                                }
                            }
                        }
                        result++;
                    }
                }
                v8 = v7 + 1;
            }

            if( n > 0 )
                n++;
            if( d1.br(r()) ) {
                boolean b = d2.br(r())
                        && d3.br(r())
                        && d4.br(r())
                        && d5.br(r());
                if( b ) {
                    evalMe:
                    if( n > 0 ) {
                        int x = d1.v() + d2.v() + d3.v() + d4.v() + d5.v() + d6.v() + d7.v() + (++n);
                        int y = d1.v() + d2.v() + d3.v() + d4.v() + d5.v() + d6.v() + d7.v() + (++n);
                        if( x > 0 ) {
                            if( y > 0 ) {
                                if( n > 0 ) {
                                    break evalMe;
                                }
                            }
                        }
                        result++;
                    }
                }
                v8 = v7 + 1;
            }

            if( n > 0 )
                n++;
            if( d1.br(r()) ) {
                boolean b = d2.br(r())
                        && d3.br(r())
                        && d4.br(r())
                        && d5.br(r());
                if( b ) {
                    evalMe:
                    if( n > 0 ) {
                        int x = d1.v() + d2.v() + d3.v() + d4.v() + d5.v() + d6.v() + d7.v() + (++n);
                        int y = d1.v() + d2.v() + d3.v() + d4.v() + d5.v() + d6.v() + d7.v() + (++n);
                        if( x > 0 ) {
                            if( y > 0 ) {
                                if( n > 0 ) {
                                    break evalMe;
                                }
                            }
                        }
                        result++;
                    }
                }
                v8 = v7 + 1;
            }

            if( n > 0 )
                n++;
            if( d1.br(r()) ) {
                boolean b = d2.br(r())
                        && d3.br(r())
                        && d4.br(r())
                        && d5.br(r());
                if( b ) {
                    evalMe:
                    if( n > 0 ) {
                        int x = d1.v() + d2.v() + d3.v() + d4.v() + d5.v() + d6.v() + d7.v() + (++n);
                        int y = d1.v() + d2.v() + d3.v() + d4.v() + d5.v() + d6.v() + d7.v() + (++n);
                        if( x > 0 ) {
                            if( y > 0 ) {
                                if( n > 0 ) {
                                    break evalMe;
                                }
                            }
                        }
                        result++;
                    }
                }
                v8 = v7 + 1;
            }

            if( n > 0 )
                n++;
            if( d1.br(r()) ) {
                boolean b = d2.br(r())
                        && d3.br(r())
                        && d4.br(r())
                        && d5.br(r());
                if( b ) {
                    evalMe:
                    if( n > 0 ) {
                        int x = d1.v() + d2.v() + d3.v() + d4.v() + d5.v() + d6.v() + d7.v() + (++n);
                        int y = d1.v() + d2.v() + d3.v() + d4.v() + d5.v() + d6.v() + d7.v() + (++n);
                        if( x > 0 ) {
                            if( y > 0 ) {
                                if( n > 0 ) {
                                    break evalMe;
                                }
                            }
                        }
                        result++;
                    }
                }
                v8 = v7 + 1;
            }

            if( n > 0 )
                n++;
            if( d1.br(r()) ) {
                boolean b = d2.br(r())
                        && d3.br(r())
                        && d4.br(r())
                        && d5.br(r());
                if( b ) {
                    evalMe:
                    if( n > 0 ) {
                        int x = d1.v() + d2.v() + d3.v() + d4.v() + d5.v() + d6.v() + d7.v() + (++n);
                        int y = d1.v() + d2.v() + d3.v() + d4.v() + d5.v() + d6.v() + d7.v() + (++n);
                        if( x > 0 ) {
                            if( y > 0 ) {
                                if( n > 0 ) {
                                    break evalMe;
                                }
                            }
                        }
                        result++;
                    }
                }
                v8 = v7 + 1;
            }

            if( n > 0 )
                n++;
            if( d1.br(r()) ) {
                boolean b = d2.br(r())
                        && d3.br(r())
                        && d4.br(r())
                        && d5.br(r());
                if( b ) {
                    evalMe:
                    if( n > 0 ) {
                        int x = d1.v() + d2.v() + d3.v() + d4.v() + d5.v() + d6.v() + d7.v() + (++n);
                        int y = d1.v() + d2.v() + d3.v() + d4.v() + d5.v() + d6.v() + d7.v() + (++n);
                        if( x > 0 ) {
                            if( y > 0 ) {
                                if( n > 0 ) {
                                    break evalMe;
                                }
                            }
                        }
                        result++;
                    }
                }
                v8 = v7 + 1;
            }

            if( n > 0 )
                n++;
            if( d1.br(r()) ) {
                boolean b = d2.br(r())
                        && d3.br(r())
                        && d4.br(r())
                        && d5.br(r());
                if( b ) {
                    evalMe:
                    if( n > 0 ) {
                        int x = d1.v() + d2.v() + d3.v() + d4.v() + d5.v() + d6.v() + d7.v() + (++n);
                        int y = d1.v() + d2.v() + d3.v() + d4.v() + d5.v() + d6.v() + d7.v() + (++n);
                        if( x > 0 ) {
                            if( y > 0 ) {
                                if( n > 0 ) {
                                    break evalMe;
                                }
                            }
                        }
                        result++;
                    }
                }
                v8 = v7 + 1;
            }

            if( n > 0 )
                n++;
            if( d1.br(r()) ) {
                boolean b = d2.br(r())
                        && d3.br(r())
                        && d4.br(r())
                        && d5.br(r());
                if( b ) {
                    evalMe:
                    if( n > 0 ) {
                        int x = d1.v() + d2.v() + d3.v() + d4.v() + d5.v() + d6.v() + d7.v() + (++n);
                        int y = d1.v() + d2.v() + d3.v() + d4.v() + d5.v() + d6.v() + d7.v() + (++n);
                        if( x > 0 ) {
                            if( y > 0 ) {
                                if( n > 0 ) {
                                    break evalMe;
                                }
                            }
                        }
                        result++;
                    }
                }
                v8 = v7 + 1;
            }

            if( n > 0 )
                n++;
            if( d1.br(r()) ) {
                boolean b = d2.br(r())
                        && d3.br(r())
                        && d4.br(r())
                        && d5.br(r());
                if( b ) {
                    evalMe:
                    if( n > 0 ) {
                        int x = d1.v() + d2.v() + d3.v() + d4.v() + d5.v() + d6.v() + d7.v() + (++n);
                        int y = d1.v() + d2.v() + d3.v() + d4.v() + d5.v() + d6.v() + d7.v() + (++n);
                        if( x > 0 ) {
                            if( y > 0 ) {
                                if( n > 0 ) {
                                    break evalMe;
                                }
                            }
                        }
                        result++;
                    }
                }
                v8 = v7 + 1;
            }

            if( n > 0 )
                n++;
            if( d1.br(r()) ) {
                boolean b = d2.br(r())
                        && d3.br(r())
                        && d4.br(r())
                        && d5.br(r());
                if( b ) {
                    evalMe:
                    if( n > 0 ) {
                        int x = d1.v() + d2.v() + d3.v() + d4.v() + d5.v() + d6.v() + d7.v() + (++n);
                        int y = d1.v() + d2.v() + d3.v() + d4.v() + d5.v() + d6.v() + d7.v() + (++n);
                        if( x > 0 ) {
                            if( y > 0 ) {
                                if( n > 0 ) {
                                    break evalMe;
                                }
                            }
                        }
                        result++;
                    }
                }
                v8 = v7 + 1;
            }

            if( n > 0 )
                n++;
            if( d1.br(r()) ) {
                boolean b = d2.br(r())
                        && d3.br(r())
                        && d4.br(r())
                        && d5.br(r());
                if( b ) {
                    evalMe:
                    if( n > 0 ) {
                        int x = d1.v() + d2.v() + d3.v() + d4.v() + d5.v() + d6.v() + d7.v() + (++n);
                        int y = d1.v() + d2.v() + d3.v() + d4.v() + d5.v() + d6.v() + d7.v() + (++n);
                        if( x > 0 ) {
                            if( y > 0 ) {
                                if( n > 0 ) {
                                    break evalMe;
                                }
                            }
                        }
                        result++;
                    }
                }
                v8 = v7 + 1;
            }

            if( n > 0 )
                n++;
            if( d1.br(r()) ) {
                boolean b = d2.br(r())
                        && d3.br(r())
                        && d4.br(r())
                        && d5.br(r());
                if( b ) {
                    evalMe:
                    if( n > 0 ) {
                        int x = d1.v() + d2.v() + d3.v() + d4.v() + d5.v() + d6.v() + d7.v() + (++n);
                        int y = d1.v() + d2.v() + d3.v() + d4.v() + d5.v() + d6.v() + d7.v() + (++n);
                        if( x > 0 ) {
                            if( y > 0 ) {
                                if( n > 0 ) {
                                    break evalMe;
                                }
                            }
                        }
                        result++;
                    }
                }
                v8 = v7 + 1;
            }

            if( n > 0 )
                n++;
            if( d1.br(r()) ) {
                boolean b = d2.br(r())
                        && d3.br(r())
                        && d4.br(r())
                        && d5.br(r());
                if( b ) {
                    evalMe:
                    if( n > 0 ) {
                        int x = d1.v() + d2.v() + d3.v() + d4.v() + d5.v() + d6.v() + d7.v() + (++n);
                        int y = d1.v() + d2.v() + d3.v() + d4.v() + d5.v() + d6.v() + d7.v() + (++n);
                        if( x > 0 ) {
                            if( y > 0 ) {
                                if( n > 0 ) {
                                    break evalMe;
                                }
                            }
                        }
                        result++;
                    }
                }
                v8 = v7 + 1;
            }

            if( n > 0 )
                n++;
            if( d1.br(r()) ) {
                boolean b = d2.br(r())
                        && d3.br(r())
                        && d4.br(r())
                        && d5.br(r());
                if( b ) {
                    evalMe:
                    if( n > 0 ) {
                        int x = d1.v() + d2.v() + d3.v() + d4.v() + d5.v() + d6.v() + d7.v() + (++n);
                        int y = d1.v() + d2.v() + d3.v() + d4.v() + d5.v() + d6.v() + d7.v() + (++n);
                        if( x > 0 ) {
                            if( y > 0 ) {
                                if( n > 0 ) {
                                    break evalMe;
                                }
                            }
                        }
                        result++;
                    }
                }
                v8 = v7 + 1;
            }

            if( n > 0 )
                n++;
            if( d1.br(r()) ) {
                boolean b = d2.br(r())
                        && d3.br(r())
                        && d4.br(r())
                        && d5.br(r());
                if( b ) {
                    evalMe:
                    if( n > 0 ) {
                        int x = d1.v() + d2.v() + d3.v() + d4.v() + d5.v() + d6.v() + d7.v() + (++n);
                        int y = d1.v() + d2.v() + d3.v() + d4.v() + d5.v() + d6.v() + d7.v() + (++n);
                        if( x > 0 ) {
                            if( y > 0 ) {
                                if( n > 0 ) {
                                    break evalMe;
                                }
                            }
                        }
                        result++;
                    }
                }
                v8 = v7 + 1;
            }

            if( n > 0 )
                n++;
            if( d1.br(r()) ) {
                boolean b = d2.br(r())
                        && d3.br(r())
                        && d4.br(r())
                        && d5.br(r());
                if( b ) {
                    evalMe:
                    if( n > 0 ) {
                        int x = d1.v() + d2.v() + d3.v() + d4.v() + d5.v() + d6.v() + d7.v() + (++n);
                        int y = d1.v() + d2.v() + d3.v() + d4.v() + d5.v() + d6.v() + d7.v() + (++n);
                        if( x > 0 ) {
                            if( y > 0 ) {
                                if( n > 0 ) {
                                    break evalMe;
                                }
                            }
                        }
                        result++;
                    }
                }
                v8 = v7 + 1;
            }

            if( n > 0 )
                n++;
            if( d1.br(r()) ) {
                boolean b = d2.br(r())
                        && d3.br(r())
                        && d4.br(r())
                        && d5.br(r());
                if( b ) {
                    evalMe:
                    if( n > 0 ) {
                        int x = d1.v() + d2.v() + d3.v() + d4.v() + d5.v() + d6.v() + d7.v() + (++n);
                        int y = d1.v() + d2.v() + d3.v() + d4.v() + d5.v() + d6.v() + d7.v() + (++n);
                        if( x > 0 ) {
                            if( y > 0 ) {
                                if( n > 0 ) {
                                    break evalMe;
                                }
                            }
                        }
                        result++;
                    }
                }
                v8 = v7 + 1;
            }

            if( n > 0 )
                n++;
            if( d1.br(r()) ) {
                boolean b = d2.br(r())
                        && d3.br(r())
                        && d4.br(r())
                        && d5.br(r());
                if( b ) {
                    evalMe:
                    if( n > 0 ) {
                        int x = d1.v() + d2.v() + d3.v() + d4.v() + d5.v() + d6.v() + d7.v() + (++n);
                        int y = d1.v() + d2.v() + d3.v() + d4.v() + d5.v() + d6.v() + d7.v() + (++n);
                        if( x > 0 ) {
                            if( y > 0 ) {
                                if( n > 0 ) {
                                    break evalMe;
                                }
                            }
                        }
                        result++;
                    }
                }
                v8 = v7 + 1;
            }

            if( n > 0 )
                n++;
            if( d1.br(r()) ) {
                boolean b = d2.br(r())
                        && d3.br(r())
                        && d4.br(r())
                        && d5.br(r());
                if( b ) {
                    evalMe:
                    if( n > 0 ) {
                        int x = d1.v() + d2.v() + d3.v() + d4.v() + d5.v() + d6.v() + d7.v() + (++n);
                        int y = d1.v() + d2.v() + d3.v() + d4.v() + d5.v() + d6.v() + d7.v() + (++n);
                        if( x > 0 ) {
                            if( y > 0 ) {
                                if( n > 0 ) {
                                    break evalMe;
                                }
                            }
                        }
                        result++;
                    }
                }
                v8 = v7 + 1;
            }

            if( v8 + r() > 11 ) {
                int r_v0 = v0;
                int r_v1 = v1;
                int r_v2 = v2;
                int r_v3 = v3;
                int r_v4 = v4;
                int r_v5 = v5;
                int r_v6 = v6;
                int r_v7 = v7;
                int r_v8 = v8;
                int r_v9 = v9;
                int r_va = va;
                int r_vb = vb;
                int r_vc = vc;
                int r_vd = vd;
                int r_ve = ve;
                int r_vf = vf;

                int r_sum = r_v0 + r_v1 + r_v2 + r_v3 + r_v4 + r_v5 + r_v6 + r_v7 + r_v8
                        + r_v9 + r_va + r_vb + r_vc + r_vd + r_ve + r_vf;

                if( r_sum > 0 )
                    System.out.println("r_sum: " + result);
            }

            if( v8 + r() > 11 ) {
                int r11_v0 = v0;
                int r11_v1 = v1;
                int r11_v2 = v2;
                int r11_v3 = v3;
                int r11_v4 = v4;
                int r11_v5 = v5;
                int r11_v6 = v6;
                int r11_v7 = v7;
                int r11_v8 = v8;
                int r11_v9 = v9;
                int r11_va = va;
                int r11_vb = vb;
                int r11_vc = vc;
                int r11_vd = vd;
                int r11_ve = ve;
                int r11_vf = vf;

                int r11_sum = r11_v0 + r11_v1 + r11_v2 + r11_v3 + r11_v4 + r11_v5 + r11_v6 + r11_v7 + r11_v8
                        + r11_v9 + r11_va + r11_vb + r11_vc + r11_vd + r11_ve + r11_vf;

                if( r11_sum > 0 )
                    System.out.println("r_sum: " + result);
            }

            if( v8 + r() > 11 ) {
                int r12_v0 = v0;
                int r12_v1 = v1;
                int r12_v2 = v2;
                int r12_v3 = v3;
                int r12_v4 = v4;
                int r12_v5 = v5;
                int r12_v6 = v6;
                int r12_v7 = v7;
                int r12_v8 = v8;
                int r12_v9 = v9;
                int r12_va = va;
                int r12_vb = vb;
                int r12_vc = vc;
                int r12_vd = vd;
                int r12_ve = ve;
                int r12_vf = vf;

                int r12_sum = r12_v0 + r12_v1 + r12_v2 + r12_v3 + r12_v4 + r12_v5 + r12_v6 + r12_v7 + r12_v8
                        + r12_v9 + r12_va + r12_vb + r12_vc + r12_vd + r12_ve + r12_vf;

                if( r12_sum > 0 )
                    System.out.println("r_sum: " + result);
            }

            if( v8 + r() > 11 ) {
                int r13_v0 = v0;
                int r13_v1 = v1;
                int r13_v2 = v2;
                int r13_v3 = v3;
                int r13_v4 = v4;
                int r13_v5 = v5;
                int r13_v6 = v6;
                int r13_v7 = v7;
                int r13_v8 = v8;
                int r13_v9 = v9;
                int r13_va = va;
                int r13_vb = vb;
                int r13_vc = vc;
                int r13_vd = vd;
                int r13_ve = ve;
                int r13_vf = vf;

                int r13_sum = r13_v0 + r13_v1 + r13_v2 + r13_v3 + r13_v4 + r13_v5 + r13_v6 + r13_v7 + r13_v8
                        + r13_v9 + r13_va + r13_vb + r13_vc + r13_vd + r13_ve + r13_vf;

                if( r13_sum > 0 )
                    System.out.println("r_sum: " + result);
            }

            if( v8 + r() > 11 ) {
                int r13_v0 = v0;
                int r13_v1 = v1;
                int r13_v2 = v2;
                int r13_v3 = v3;
                int r13_v4 = v4;
                int r13_v5 = v5;
                int r13_v6 = v6;
                int r13_v7 = v7;
                int r13_v8 = v8;
                int r13_v9 = v9;
                int r13_va = va;
                int r13_vb = vb;
                int r13_vc = vc;
                int r13_vd = vd;
                int r13_ve = ve;
                int r13_vf = vf;

                int r13_sum = r13_v0 + r13_v1 + r13_v2 + r13_v3 + r13_v4 + r13_v5 + r13_v6 + r13_v7 + r13_v8
                        + r13_v9 + r13_va + r13_vb + r13_vc + r13_vd + r13_ve + r13_vf;

                if( r13_sum > 0 )
                    System.out.println("r_sum: " + result);
            }

            if( v8 + r() > 11 ) {
                int r14_v0 = v0;
                int r14_v1 = v1;
                int r14_v2 = v2;
                int r14_v3 = v3;
                int r14_v4 = v4;
                int r14_v5 = v5;
                int r14_v6 = v6;
                int r14_v7 = v7;
                int r14_v8 = v8;
                int r14_v9 = v9;
                int r14_va = va;
                int r14_vb = vb;
                int r14_vc = vc;
                int r14_vd = vd;
                int r14_ve = ve;
                int r14_vf = vf;

                int r14_sum = r14_v0 + r14_v1 + r14_v2 + r14_v3 + r14_v4 + r14_v5 + r14_v6 + r14_v7 + r14_v8
                        + r14_v9 + r14_va + r14_vb + r14_vc + r14_vd + r14_ve + r14_vf;

                if( r14_sum > 0 )
                    System.out.println("r14_sum: " + result);
            }

            if( v8 + r() > 11 ) {
                int r15_v0 = v0;
                int r15_v1 = v1;
                int r15_v2 = v2;
                int r15_v3 = v3;
                int r15_v4 = v4;
                int r15_v5 = v5;
                int r15_v6 = v6;
                int r15_v7 = v7;
                int r15_v8 = v8;
                int r15_v9 = v9;
                int r15_va = va;
                int r15_vb = vb;
                int r15_vc = vc;
                int r15_vd = vd;
                int r15_ve = ve;
                int r15_vf = vf;

                int r15_sum = r15_v0 + r15_v1 + r15_v2 + r15_v3 + r15_v4 + r15_v5 + r15_v6 + r15_v7 + r15_v8
                        + r15_v9 + r15_va + r15_vb + r15_vc + r15_vd + r15_ve + r15_vf;

                if( r15_sum > 0 )
                    System.out.println("r15_sum: " + result);
            }

            if( v8 + r() > 11 ) {
                int r17_v0 = v0;
                int r17_v1 = v1;
                int r17_v2 = v2;
                int r17_v3 = v3;
                int r17_v4 = v4;
                int r17_v5 = v5;
                int r17_v6 = v6;
                int r17_v7 = v7;
                int r17_v8 = v8;
                int r17_v9 = v9;
                int r17_va = va;
                int r17_vb = vb;
                int r17_vc = vc;
                int r17_vd = vd;
                int r17_ve = ve;
                int r17_vf = vf;

                int r17_sum = r17_v0 + r17_v1 + r17_v2 + r17_v3 + r17_v4 + r17_v5 + r17_v6 + r17_v7 + r17_v8
                        + r17_v9 + r17_va + r17_vb + r17_vc + r17_vd + r17_ve + r17_vf;

                if( r17_sum > 0 )
                    System.out.println("r17_sum: " + result);
            }

            if( v8 + r() > 11 ) {
                int r18_v0 = v0;
                int r18_v1 = v1;
                int r18_v2 = v2;
                int r18_v3 = v3;
                int r18_v4 = v4;
                int r18_v5 = v5;
                int r18_v6 = v6;
                int r18_v7 = v7;
                int r18_v8 = v8;
                int r18_v9 = v9;
                int r18_va = va;
                int r18_vb = vb;
                int r18_vc = vc;
                int r18_vd = vd;
                int r18_ve = ve;
                int r18_vf = vf;

                int r18_sum = r18_v0 + r18_v1 + r18_v2 + r18_v3 + r18_v4 + r18_v5 + r18_v6 + r18_v7 + r18_v8
                        + r18_v9 + r18_va + r18_vb + r18_vc + r18_vd + r18_ve + r18_vf;

                if( r18_sum > 0 )
                    System.out.println("r18_sum: " + result);
            }

            if( v8 + r() > 11 ) {
                int r19_v0 = v0;
                int r19_v1 = v1;
                int r19_v2 = v2;
                int r19_v3 = v3;
                int r19_v4 = v4;
                int r19_v5 = v5;
                int r19_v6 = v6;
                int r19_v7 = v7;
                int r19_v8 = v8;
                int r19_v9 = v9;
                int r19_va = va;
                int r19_vb = vb;
                int r19_vc = vc;
                int r19_vd = vd;
                int r19_ve = ve;
                int r19_vf = vf;

                int r19_sum = r19_v0 + r19_v1 + r19_v2 + r19_v3 + r19_v4 + r19_v5 + r19_v6 + r19_v7 + r19_v8
                        + r19_v9 + r19_va + r19_vb + r19_vc + r19_vd + r19_ve + r19_vf;

                if( r19_sum > 0 )
                    System.out.println("r19_sum: " + result);
            }

            if( v8 + r() > 11 ) {
                int r20_v0 = v0;
                int r20_v1 = v1;
                int r20_v2 = v2;
                int r20_v3 = v3;
                int r20_v4 = v4;
                int r20_v5 = v5;
                int r20_v6 = v6;
                int r20_v7 = v7;
                int r20_v8 = v8;
                int r20_v9 = v9;
                int r20_va = va;
                int r20_vb = vb;
                int r20_vc = vc;
                int r20_vd = vd;
                int r20_ve = ve;
                int r20_vf = vf;

                int r20_sum = r20_v0 + r20_v1 + r20_v2 + r20_v3 + r20_v4 + r20_v5 + r20_v6 + r20_v7 + r20_v8
                        + r20_v9 + r20_va + r20_vb + r20_vc + r20_vd + r20_ve + r20_vf;

                if( r20_sum > 0 )
                    System.out.println("r20_sum: " + result);
            }

            if( v8 + r() > 11 ) {
                int r21_v0 = v0;
                int r21_v1 = v1;
                int r21_v2 = v2;
                int r21_v3 = v3;
                int r21_v4 = v4;
                int r21_v5 = v5;
                int r21_v6 = v6;
                int r21_v7 = v7;
                int r21_v8 = v8;
                int r21_v9 = v9;
                int r21_va = va;
                int r21_vb = vb;
                int r21_vc = vc;
                int r21_vd = vd;
                int r21_ve = ve;
                int r21_vf = vf;

                int r21_sum = r21_v0 + r21_v1 + r21_v2 + r21_v3 + r21_v4 + r21_v5 + r21_v6 + r21_v7 + r21_v8
                        + r21_v9 + r21_va + r21_vb + r21_vc + r21_vd + r21_ve + r21_vf;

                if( r21_sum > 0 )
                    System.out.println("r_sum: " + result);
            }

            if( v8 + r() > 11 ) {
                int r21_v0 = v0;
                int r21_v1 = v1;
                int r21_v2 = v2;
                int r21_v3 = v3;
                int r21_v4 = v4;
                int r21_v5 = v5;
                int r21_v6 = v6;
                int r21_v7 = v7;
                int r21_v8 = v8;
                int r21_v9 = v9;
                int r21_va = va;
                int r21_vb = vb;
                int r21_vc = vc;
                int r21_vd = vd;
                int r21_ve = ve;
                int r21_vf = vf;

                int r21_sum = r21_v0 + r21_v1 + r21_v2 + r21_v3 + r21_v4 + r21_v5 + r21_v6 + r21_v7 + r21_v8
                        + r21_v9 + r21_va + r21_vb + r21_vc + r21_vd + r21_ve + r21_vf;

                if( r21_sum > 0 )
                    System.out.println("r_sum: " + result);
            }

            if( v8 + r() > 11 ) {
                int r22_v0 = v0;
                int r22_v1 = v1;
                int r22_v2 = v2;
                int r22_v3 = v3;
                int r22_v4 = v4;
                int r22_v5 = v5;
                int r22_v6 = v6;
                int r22_v7 = v7;
                int r22_v8 = v8;
                int r22_v9 = v9;
                int r22_va = va;
                int r22_vb = vb;
                int r22_vc = vc;
                int r22_vd = vd;
                int r22_ve = ve;
                int r22_vf = vf;

                int r22_sum = r22_v0 + r22_v1 + r22_v2 + r22_v3 + r22_v4 + r22_v5 + r22_v6 + r22_v7 + r22_v8
                        + r22_v9 + r22_va + r22_vb + r22_vc + r22_vd + r22_ve + r22_vf;

                if( r22_sum > 0 )
                    System.out.println("r22_sum: " + result);
            }

            if( v8 + r() > 11 ) {
                int r23_v0 = v0;
                int r23_v1 = v1;
                int r23_v2 = v2;
                int r23_v3 = v3;
                int r23_v4 = v4;
                int r23_v5 = v5;
                int r23_v6 = v6;
                int r23_v7 = v7;
                int r23_v8 = v8;
                int r23_v9 = v9;
                int r23_va = va;
                int r23_vb = vb;
                int r23_vc = vc;
                int r23_vd = vd;
                int r23_ve = ve;
                int r23_vf = vf;

                int r23_sum = r23_v0 + r23_v1 + r23_v2 + r23_v3 + r23_v4 + r23_v5 + r23_v6 + r23_v7 + r23_v8
                        + r23_v9 + r23_va + r23_vb + r23_vc + r23_vd + r23_ve + r23_vf;

                if( r23_sum > 0 )
                    System.out.println("r23_sum: " + result);
            }

            if( v8 + r() > 11 ) {
                int r24_v0 = v0;
                int r24_v1 = v1;
                int r24_v2 = v2;
                int r24_v3 = v3;
                int r24_v4 = v4;
                int r24_v5 = v5;
                int r24_v6 = v6;
                int r24_v7 = v7;
                int r24_v8 = v8;
                int r24_v9 = v9;
                int r24_va = va;
                int r24_vb = vb;
                int r24_vc = vc;
                int r24_vd = vd;
                int r24_ve = ve;
                int r24_vf = vf;

                int r24_sum = r24_v0 + r24_v1 + r24_v2 + r24_v3 + r24_v4 + r24_v5 + r24_v6 + r24_v7 + r24_v8
                        + r24_v9 + r24_va + r24_vb + r24_vc + r24_vd + r24_ve + r24_vf;

                if( r24_sum > 0 )
                    System.out.println("r24_sum: " + result);
            }

            if( v8 + r() > 11 ) {
                int r25_v0 = v0;
                int r25_v1 = v1;
                int r25_v2 = v2;
                int r25_v3 = v3;
                int r25_v4 = v4;
                int r25_v5 = v5;
                int r25_v6 = v6;
                int r25_v7 = v7;
                int r25_v8 = v8;
                int r25_v9 = v9;
                int r25_va = va;
                int r25_vb = vb;
                int r25_vc = vc;
                int r25_vd = vd;
                int r25_ve = ve;
                int r25_vf = vf;

                int r25_sum = r25_v0 + r25_v1 + r25_v2 + r25_v3 + r25_v4 + r25_v5 + r25_v6 + r25_v7 + r25_v8
                        + r25_v9 + r25_va + r25_vb + r25_vc + r25_vd + r25_ve + r25_vf;

                if( r25_sum > 0 )
                    System.out.println("r25_sum: " + result);
            }

            if( v8 + r() > 11 ) {
                int r26_v0 = v0;
                int r26_v1 = v1;
                int r26_v2 = v2;
                int r26_v3 = v3;
                int r26_v4 = v4;
                int r26_v5 = v5;
                int r26_v6 = v6;
                int r26_v7 = v7;
                int r26_v8 = v8;
                int r26_v9 = v9;
                int r26_va = va;
                int r26_vb = vb;
                int r26_vc = vc;
                int r26_vd = vd;
                int r26_ve = ve;
                int r26_vf = vf;

                int r26_sum = r26_v0 + r26_v1 + r26_v2 + r26_v3 + r26_v4 + r26_v5 + r26_v6 + r26_v7 + r26_v8
                        + r26_v9 + r26_va + r26_vb + r26_vc + r26_vd + r26_ve + r26_vf;

                if( r26_sum > 0 )
                    System.out.println("r26_sum: " + result);
            }

            if( v8 + r() > 11 ) {
                int r27_v0 = v0;
                int r27_v1 = v1;
                int r27_v2 = v2;
                int r27_v3 = v3;
                int r27_v4 = v4;
                int r27_v5 = v5;
                int r27_v6 = v6;
                int r27_v7 = v7;
                int r27_v8 = v8;
                int r27_v9 = v9;
                int r27_va = va;
                int r27_vb = vb;
                int r27_vc = vc;
                int r27_vd = vd;
                int r27_ve = ve;
                int r27_vf = vf;

                int r27_sum = r27_v0 + r27_v1 + r27_v2 + r27_v3 + r27_v4 + r27_v5 + r27_v6 + r27_v7 + r27_v8
                        + r27_v9 + r27_va + r27_vb + r27_vc + r27_vd + r27_ve + r27_vf;

                if( r27_sum > 0 )
                    System.out.println("r_sum: " + result);
            }

            if( v8 + r() > 11 ) {
                int r1_v0 = v0;
                int r1_v1 = v1;
                int r1_v2 = v2;
                int r1_v3 = v3;
                int r1_v4 = v4;
                int r1_v5 = v5;
                int r1_v6 = v6;
                int r1_v7 = v7;
                int r1_v8 = v8;
                int r1_v9 = v9;
                int r1_va = va;
                int r1_vb = vb;
                int r1_vc = vc;
                int r1_vd = vd;
                int r1_ve = ve;
                int r1_vf = vf;

                int r1_sum = r1_v0 + r1_v1 + r1_v2 + r1_v3 + r1_v4 + r1_v5 + r1_v6 + r1_v7 + r1_v8
                        + r1_v9 + r1_va + r1_vb + r1_vc + r1_vd + r1_ve + r1_vf;

                if( r1_sum > 0 )
                    System.out.println("r_sum: " + result);
            }

            if( v8 + r() > 11 ) {
                int r_v0 = v0;
                int r_v1 = v1;
                int r_v2 = v2;
                int r_v3 = v3;
                int r_v4 = v4;
                int r_v5 = v5;
                int r_v6 = v6;
                int r_v7 = v7;
                int r_v8 = v8;
                int r_v9 = v9;
                int r_va = va;
                int r_vb = vb;
                int r_vc = vc;
                int r_vd = vd;
                int r_ve = ve;
                int r_vf = vf;

                int r_sum = r_v0 + r_v1 + r_v2 + r_v3 + r_v4 + r_v5 + r_v6 + r_v7 + r_v8
                        + r_v9 + r_va + r_vb + r_vc + r_vd + r_ve + r_vf;

                if( r_sum > 0 )
                    System.out.println("r_sum: " + result);
            }
        }
    }

    private static int r() {
        return (int)(Math.random() * 100);
    }

    static class Data {
        public boolean b() {
            return true;
        }
        public boolean br(double f) {
            return true;
        }
        public int v() {
            return (int)(r() * 100);
        }
    }

    static class Data1 extends Data {
        public int d1;
    }
    static class Data2 extends Data {
        public int d2;
    }
    static class Data3 extends Data {
        public int d3;
    }
    static class Data4 extends Data {
        public int d4;
    }
    static class Data5 extends Data {
        public int d5;
    }
    static class Data6 extends Data {
        public int d6;
    }
    static class Data7 extends Data {
        public int d7;
    }
}