# -static is used for non-glibc distros.
# Use the oldest toolchain/kernel available, since kernel ABI is backward, but not future compatible
wsl_get_ip: wsl_get_ip.c
	cc wsl_get_ip.c -static -o wsl_get_ip -O3
