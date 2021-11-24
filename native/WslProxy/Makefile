# Where to unpack musl
MUSL_DISTR := musl
# Where to install musl
MUSL_HOME := $(HOME)/musl
# Musl gcc wrapper (use it co compile)
MUSL_CC := $(MUSL_HOME)/bin/musl-gcc

CFLAGS = -Wall -Wextra -pedantic -Werror -Os -D_POSIX_SOURCE=1
LDLIBS = -static
CC = $(MUSL_CC)


ifeq ("$(wildcard $(MUSL_DISTR))", "")
$(error Install $(MUSL_DISTR), see README.txt)
endif

all: $(MUSL_CC) wslproxy

$(MUSL_CC): musl
	cd musl && ./configure --prefix=$(MUSL_HOME) --syslibdir=$(MUSL_HOME)/lib && $(MAKE) && $(MAKE) install

wslproxy: $(SOURCES)

.PHONY: all clean

clean:
	rm wslproxy