package org.example;

class Main_CodeFlow {

  public static void main(String[] args) {
    new Tester().testMe(1);
  }

  static class Tester {
    public boolean chk() {
      return true;
    }

    public void <weak_warning descr="Method 'testMe' is too complex to analyze by data flow algorithm">testMe</weak_warning>(int i) {
      Data1 d1 = (i < 0) ? null : new Data1();
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