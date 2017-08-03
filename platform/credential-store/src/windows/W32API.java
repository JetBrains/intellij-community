/* Copyright (c) 2007 Timothy Wall, All Rights Reserved
 * 
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 * 
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.  
 */
package com.intellij.credentialStore.windows;

import com.sun.jna.*;
import com.sun.jna.ptr.ByReference;
import com.sun.jna.win32.StdCallLibrary;

/** Base type for most W32 API libraries.  Provides standard options
 * for unicode/ASCII mappings.  Set the system property {@code w32.ascii}
 * to {@code true} to default to the ASCII mappings.
 */
@SuppressWarnings("serial")
public interface W32API extends StdCallLibrary {    
    class HANDLE extends PointerType {
        private boolean immutable;
        public HANDLE() { }
        public HANDLE(Pointer p) { setPointer(p); immutable = true; }
        
        /** Override to the appropriate object for INVALID_HANDLE_VALUE. */
        public Object fromNative(Object nativeValue, FromNativeContext context) {
            Object o = super.fromNative(nativeValue, context);
            if (INVALID_HANDLE_VALUE.equals(o))
                return INVALID_HANDLE_VALUE;
            return o;
        }
        
        public void setPointer(Pointer p) {
            if (immutable)
                throw new UnsupportedOperationException("immutable reference");
            super.setPointer(p);
        }
    }
    
	class WORD extends IntegerType {
    	public WORD() { this(0); }
    	public WORD(long value) { super(2, value); } 
    }
	
	class DWORD extends IntegerType {
    	public DWORD() { this(0); }
    	public DWORD(long value) { super(4, value); } 
    }
	
	class LONG extends IntegerType {
    	public LONG() { this(0); }
    	public LONG(long value) { super(Native.LONG_SIZE, value); } 
    }
    
    class HDC extends HANDLE { }
    class HICON extends HANDLE { }
    class HBITMAP extends HANDLE { }
    class HRGN extends HANDLE { }
    class HWND extends HANDLE {
        public HWND() { }
        public HWND(Pointer p) { super(p); }
    }
    class HINSTANCE extends HANDLE { }
    class HMODULE extends HINSTANCE { }
    
    /**
     * The HRESULT data type is a 32-bit value is used to describe an error or warning.
     */
    class HRESULT extends NativeLong {
    	public HRESULT() {
    		
    	}
    	
    	public HRESULT(int value) {
    		super(value);
    	}
    }

    /** Constant value representing an invalid HANDLE. */
    HANDLE INVALID_HANDLE_VALUE = new HANDLE(Pointer.createConstant(Pointer.SIZE==8?-1:0xFFFFFFFFL));

    /** Special HWND value. */
    HWND HWND_BROADCAST = new HWND(Pointer.createConstant(0xFFFF));

    /** LPHANDLE */
    class HANDLEByReference extends ByReference {
        
    	public HANDLEByReference() {
            this(null);
        }
        
        public HANDLEByReference(HANDLE h) {
            super(Pointer.SIZE);
            setValue(h);
        }
        
        public void setValue(HANDLE h) {
            getPointer().setPointer(0, h != null ? h.getPointer() : null);
        }
        
        public HANDLE getValue() {
            Pointer p = getPointer().getPointer(0);
            if (p == null)
                return null;
            if (INVALID_HANDLE_VALUE.getPointer().equals(p)) 
                return INVALID_HANDLE_VALUE;
            HANDLE h = new HANDLE();
            h.setPointer(p);
            return h;
        }
    }
    
    class LONG_PTR extends IntegerType { 
        public LONG_PTR() { this(0); }
        public LONG_PTR(long value) { super(Pointer.SIZE, value); }
    }
    
    class SSIZE_T extends LONG_PTR {
        public SSIZE_T() { this(0); }
        public SSIZE_T(long value) { super(value); }
    }
    
    class ULONG_PTR extends IntegerType { 
        public ULONG_PTR() { this(0); }
        public ULONG_PTR(long value) { super(Pointer.SIZE, value); }
    }
    
    class SIZE_T extends ULONG_PTR {
        public SIZE_T() { this(0); }
        public SIZE_T(long value) { super(value); }
    }
    
    class LPARAM extends LONG_PTR { 
        public LPARAM() { this(0); }
        public LPARAM(long value) { super(value); }
    } 
    
    class LRESULT extends LONG_PTR { 
        public LRESULT() { this(0); }
        public LRESULT(long value) { super(value); }
    }

    class UINT_PTR extends IntegerType {
        public UINT_PTR() { super(Pointer.SIZE); }
        public UINT_PTR(long value) { super(Pointer.SIZE, value); }
        public Pointer toPointer() { return Pointer.createConstant(longValue()); }
    }

    class WPARAM extends UINT_PTR {
        public WPARAM() { this(0); }
        public WPARAM(long value) { super(value); }
    }
}
