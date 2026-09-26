# Machine info cell

Stdlib-only (no psutil), works on Windows, macOS, and Linux:

```python
import platform, subprocess

def _cpu_name() -> str:
    s = platform.system()
    try:
        if s == 'Windows':
            # wmic is deprecated on Windows 11; use PowerShell CIM instead.
            return subprocess.check_output(
                ['powershell', '-NoProfile', '-Command',
                 '(Get-CimInstance Win32_Processor).Name'],
                stderr=subprocess.DEVNULL,
            ).decode().strip()
        if s == 'Darwin':
            return subprocess.check_output(
                ['sysctl', '-n', 'machdep.cpu.brand_string'], stderr=subprocess.DEVNULL
            ).decode().strip()
        # Linux
        for line in open('/proc/cpuinfo'):
            if line.startswith('model name'):
                return line.split(':', 1)[1].strip()
    except Exception:
        pass
    return platform.processor() or 'unknown'

def _ram_gb() -> float:
    s = platform.system()
    try:
        if s == 'Windows':
            import ctypes
            class _M(ctypes.Structure):
                _fields_ = [
                    ('dwLength',                ctypes.c_ulong),
                    ('dwMemoryLoad',            ctypes.c_ulong),
                    ('ullTotalPhys',            ctypes.c_ulonglong),
                    ('ullAvailPhys',            ctypes.c_ulonglong),
                    ('ullTotalPageFile',        ctypes.c_ulonglong),
                    ('ullAvailPageFile',        ctypes.c_ulonglong),
                    ('ullTotalVirtual',         ctypes.c_ulonglong),
                    ('ullAvailVirtual',         ctypes.c_ulonglong),
                    ('sullAvailExtendedVirtual',ctypes.c_ulonglong),
                ]
            m = _M()
            m.dwLength = ctypes.sizeof(m)
            ctypes.windll.kernel32.GlobalMemoryStatusEx(ctypes.byref(m))
            return m.ullTotalPhys / 1024**3
        if s == 'Darwin':
            out = subprocess.check_output(
                ['sysctl', '-n', 'hw.memsize'], stderr=subprocess.DEVNULL
            ).decode().strip()
            return int(out) / 1024**3
        # Linux
        for line in open('/proc/meminfo'):
            if line.startswith('MemTotal'):
                return int(line.split()[1]) / 1024**2
    except Exception:
        pass
    return float('nan')

print(f"Host : {platform.node()}")
print(f"OS   : {platform.system()} {platform.release()} ({platform.version()})")
print(f"CPU  : {_cpu_name()}")
print(f"RAM  : {_ram_gb():.0f} GB")
```
