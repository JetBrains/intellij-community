MUSL_VER := 1.2.2
# Where to unpack musl
MUSL_DISTR := musl
# Where to install musl
MUSL_HOME := $(HOME)/musl
# Musl gcc wrapper (use it co compile)
MUSL_CC := $(MUSL_HOME)/bin/musl-gcc

CFLAGS = -Wall -Wextra -pedantic -Werror -Os -D_POSIX_SOURCE=1 -D_BSD_SOURCE=1
LDLIBS = -static
CC = $(MUSL_CC)

all: $(MUSL_CC) wslproxy wslhash

musl:
	@echo I will now download musl. If it fails, check you have wget and see README
	wget https://musl.libc.org/releases/musl-$(MUSL_VER).tar.gz -O musl.tar.gz
	tar xfvz musl.tar.gz && mv musl-$(MUSL_VER) $(MUSL_DISTR)

$(MUSL_CC): musl
	cd $(MUSL_DISTR) && ./configure --prefix=$(MUSL_HOME) --syslibdir=$(MUSL_HOME)/lib && $(MAKE) && $(MAKE) install

wslproxy: $(SOURCES) | $(MUSL_CC)
wslhash: wslhash.c xxhash.h xxhash.c | $(MUSL_CC)

.PHONY: all clean

clean:
	$(RM) wslproxy wslhash