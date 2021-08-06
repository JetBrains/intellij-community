#!/usr/local/bin/python3 -w
import sys
import struct

from ds_store import *
from mac_alias import *

"""
Script generates needed configuration for MacOS installation dialog.
Original available docs for options are in perl module
https://metacpan.org/dist/Mac-Finder-DSStore/view/DSStoreFormat.pod

Python module docs are not so great:
https://ds-store.readthedocs.io/en/latest/

Many options, which are used here could be found in 
https://dmgbuild.readthedocs.io/en/latest/settings.html
https://github.com/al45tair/dmgbuild/blob/master/dmgbuild/core.py 

Some details about binary formats
https://0day.work/parsing-the-ds_store-file-format/
  
"""
name = sys.argv[1]
bgPic = sys.argv[2]
mountName = sys.argv[3]
print(f"Process ds store in /Volumes/{mountName}/.DS_Store")
with DSStore.open(f"/Volumes/{mountName}/.DS_Store", "w+") as d:

    d[".background"]["Iloc"] = (560, 170)
    d[".DS_Store"]["Iloc"] = (610, 170)
    d[".fseventsd"]["Iloc"] = (660, 170)
    d[".Trashes"]["Iloc"] = (710, 170)
    d["Applications"]["Iloc"] = (340, 167)

    byte_stream = struct.pack('>H', 100) + struct.pack('>H', 400) + \
                  struct.pack('>H', 396) + struct.pack('>H', 855) + \
                  bytes('icnv', 'ascii') + bytearray([0] * 4)
    d['.']['fwi0'] = ('blob', byte_stream)
    d['.']['fwsw'] = ('long', 170)
    d['.']['fwvh'] = ('shor', 296)
    d['.']['ICVO'] = ('bool', True)

    alias = Alias.for_file(f"/Volumes/{mountName}/.background/{bgPic}")
    d['.']['icvp'] = {
        'viewOptionsVersion': 1,
        'backgroundType': 2,
        'backgroundImageAlias': alias.to_bytes(),
        'backgroundColorRed': 1.0,
        'backgroundColorGreen': 1.0,
        'backgroundColorBlue': 1.0,
        'gridOffsetX': 0,
        'gridOffsetY': 0,
        'gridSpacing': 100,

        'arrangeBy': 'none',
        'showIconPreview': True,
        'showItemInfo': False,
        'labelOnBottom': True,
        'textSize': 12.0,
        'iconSize': 100.0,
        'scrollPositionX': 0.0,
        'scrollPositionY': 0.0
    }
    d['.']['icvt'] = ('shor', 12)

    d[f"{name}.app"]["Iloc"] = (110, 167)

